package com.houssen.liberoshop.service.delivery;

import com.houssen.liberoshop.entity.DeliveryNote;
import com.houssen.liberoshop.entity.LossReason;
import com.houssen.liberoshop.entity.StockLoss;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.SupplyCharge;
import com.houssen.liberoshop.entity.SupplyUseCharge;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.service.StockChangedEvent;
import com.houssen.liberoshop.service.StockService;
import com.houssen.liberoshop.service.delivery.dto.ChargeBasis;
import com.houssen.liberoshop.service.delivery.dto.DeliveryLineRequest;
import com.houssen.liberoshop.service.delivery.dto.FullDeliveryRequest;
import com.houssen.liberoshop.service.delivery.dto.LossReasonCode;
import com.houssen.liberoshop.service.delivery.dto.LossRequest;
import com.houssen.liberoshop.service.delivery.dto.SalePriceRequest;
import com.houssen.liberoshop.service.delivery.dto.SalePriceSource;
import com.houssen.liberoshop.service.delivery.dto.SupplyChargeRequest;
import com.houssen.liberoshop.service.delivery.dto.TransformationInput;
import com.houssen.liberoshop.service.delivery.dto.TransformationRequest;
import com.houssen.liberoshop.util.Quantities;
import com.houssen.liberoshop.web.dto.CreateDeliveryRequest;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import com.houssen.liberoshop.web.dto.SupplyResponse;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// A ADAPTER : packages de ces classes, les memes que dans StockService
import com.houssen.liberoshop.exception.BusinessRuleException;
import com.houssen.liberoshop.repository.DeliveryNoteRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.repository.SupplyChargeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * A whole delivery note booked at once: ten products from one supplier, one "Enregistrer".
 *
 * <p>Each line is a receipt like any other ({@link StockService#registerSupply}): the same unit
 * resolution, the same cost averaging, the same movement in the product's history. What this adds
 * is that they stand or fall together: a line refused -- an unknown product, a quantity too
 * precise -- leaves the stock as it was, so a note is never half booked.
 *
 * <p>The products are locked in the order of their ids, as a sale does, so two deliveries
 * sharing products cannot wait on each other.
 */
@Service
public class DeliveryService {

    private final StockService stock;
    private final TransformationService transformations;
    private final DeliveryNoteRepository notes;
    private final SupplyChargeRepository supplyCharges;
    private final StockMovementRepository movements;
    private final ApplicationEventPublisher events;

    public DeliveryService(StockService stock,
                           TransformationService transformations,
                           DeliveryNoteRepository notes,
                           SupplyChargeRepository supplyCharges,
                           StockMovementRepository movements,
                           ApplicationEventPublisher events) {
        this.stock = stock;
        this.transformations = transformations;
        this.notes = notes;
        this.supplyCharges = supplyCharges;
        this.movements = movements;
        this.events = events;
    }

    /** @return the receipts, in the order of the note's lines */

    @Transactional
    public List<SupplyResponse> receive(CreateDeliveryRequest request, UserApp receiver) {
        List<CreateDeliveryRequest.Line> lines = request.lines();
        SupplyResponse[] booked = new SupplyResponse[lines.size()];
        IntStream.range(0, lines.size()).boxed()
                .sorted(Comparator.comparing(i -> lines.get(i).productId()))
                .forEach(i -> booked[i] = stock.registerSupply(asSupply(request.supplierId(), lines.get(i)), receiver));
        return new ArrayList<>(Arrays.asList(booked));
    }

    private static CreateSupplyRequest asSupply(Long supplierId, CreateDeliveryRequest.Line line) {
        return new CreateSupplyRequest(line.productId(), supplierId, line.packagingId(), line.quantity(),
                line.unitCost());
    }

    // ===================================================================================
    //  A note with charges, losses, a transformation and sale prices
    // ===================================================================================

    /**
     * The same note as {@link #receive}, plus what came with it, all in one transaction:
     * <ol>
     *   <li>a {@link DeliveryNote}, which groups everything below;</li>
     *   <li>one receipt per line, whose average cost already carries its share of the charges and
     *       is spread over what is left after the losses on the way;</li>
     *   <li>the charges, each linked to the receipts that carry a share of it;</li>
     *   <li>the losses, as stock movements linked to their receipt;</li>
     *   <li>the transformation, when there is one (declared, not written yet);</li>
     *   <li>the sale prices (declared, not written yet).</li>
     * </ol>
     *
     * @return the receipts, in the order of the note's lines
     */
    @Transactional
    public List<SupplyResponse> registerDeliveryWithCharge(@Valid FullDeliveryRequest request, UserApp receiver) {
        List<DeliveryLineRequest> lines = request.lines();
        List<SupplyChargeRequest> charges = orEmpty(request.charges());
        List<LossRequest> losses = orEmpty(request.losses());
        List<SalePriceRequest> salePrices = orEmpty(request.salePrices());
        int n = lines.size();

        validate(request);

        // PROCESS losses belong to the transformation; the others happen to a receipt.
        List<LossRequest> lineLosses = losses.stream()
                .filter(l -> l.reason() != LossReasonCode.PROCESS).toList();
        List<LossRequest> processLosses = losses.stream()
                .filter(l -> l.reason() == LossReasonCode.PROCESS).toList();

        // 1. The note comes first: the receipts point at it. Date and supplier are set once the
        //    first receipt is booked, so they are the movements' own.
        DeliveryNote note = new DeliveryNote();
        note.setDate(LocalDateTime.now());
        note.setPerformedBy(receiver);
        note.setWithTransformation(request.transformation() != null);
        notes.save(note);

        // 2. What each line carries on top of its price, and what it loses on the way.
        List<Map<Integer, BigDecimal>> allocations = charges.stream()
                .map(c -> allocate(c, lines)).toList();
        BigDecimal[] extra = zeros(n);
        BigDecimal[] lost = zeros(n);
        allocations.forEach(shares -> shares.forEach((i, share) -> extra[i] = extra[i].add(share)));
        lineLosses.forEach(l -> lost[l.lineIndex()] = lost[l.lineIndex()].add(l.quantity()));

        // 3. The receipts, locked in the order of the product ids.
        Supply[] booked = new Supply[n];
        IntStream.range(0, n).boxed()
                .sorted(Comparator.comparing(i -> lines.get(i).productId()))
                .forEach(i -> booked[i] = stock.bookSupply(
                        asSupply(request.supplierId(), lines.get(i)), receiver, extra[i], lost[i], note));

        note.setSupplier(booked[0].getSupplier());
        note.setDate(booked[0].getMovementDate());
        note.setPurchaseTotal(Arrays.stream(booked)
                .map(Supply::totalCost)
                .filter(total -> total != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        note.setChargesTotal(charges.stream()
                .map(SupplyChargeRequest::cust)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 4. The charges, each linked to the receipts that carry a part of it.
        for (int c = 0; c < charges.size(); c++) {
            SupplyChargeRequest req = charges.get(c);
            SupplyCharge charge = new SupplyCharge();
            charge.setRaison(req.raison().trim());
            charge.setCust(req.cust());
            charge.setGeneral(req.general());
            charge.setBasis(req.basis() == ChargeBasis.UNIT ? "unit" : "lot");
            charge.setDeliveryNote(note);
            allocations.get(c).forEach((i, share) -> {
                SupplyUseCharge use = new SupplyUseCharge();
                use.setCharge(charge);
                use.setSupply(booked[i]);
                use.setShare(share);
                charge.getSupplys().add(use);
            });
            supplyCharges.save(charge);          // the uses are saved with it (cascade)
            note.getCharges().add(charge);
        }

        // 5. The losses on the way: quantity only, their cost is carried by what is left.
        for (LossRequest loss : lineLosses) {
            Supply receipt = booked[loss.lineIndex()];
            movements.save(StockLoss.builder()
                    .quantity(Quantities.scaled(loss.quantity().multiply(receipt.factor())))
                    .movementDate(receipt.getMovementDate())
                    .product(receipt.getProduct())
                    .performedBy(receiver)
                    .reason(LossReason.valueOf(loss.reason().name()))
                    .supply(receipt)
                    .build());
        }

        // 6. The transformation: declared only, refuses the note until it is written.
        if (request.transformation() != null) {
            transformations.transform(note, request.transformation(), processLosses, booked, receiver);
        }

        // 7. The sale prices: declared only.
        applySalePrices(note, salePrices, booked);

        events.publishEvent(new StockChangedEvent(Arrays.stream(booked)
                .map(s -> s.getProduct().getId()).distinct().toList()));

        return Arrays.stream(booked).map(SupplyResponse::of).toList();
    }

    /**
     * To write later: the price of each unit a product is sold in, from {@code salePrices}
     * ({@code LINE} = a line of the note, {@code OUTPUT} = a product obtained). Not written because
     * where a price per unit lives (the packaging? the product?) is still to be decided.
     */
    private void applySalePrices(DeliveryNote note, List<SalePriceRequest> prices, Supply[] booked) {
        // TODO
    }

    // ===================================================================================
    //  Charges : who carries what
    // ===================================================================================

    /**
     * Spreads a charge over the lines it is for: a charge on one product is carried by that
     * receipt alone; a general one is shared in proportion to what each line cost (to its quantity
     * when no price was given). The last line takes the rounding, so the shares add up to the
     * charge to the ariary.
     *
     * @return the share of each line, by line index
     */
    private static Map<Integer, BigDecimal> allocate(SupplyChargeRequest charge, List<DeliveryLineRequest> lines) {
        List<Integer> targets = (charge.general() || charge.lineIndexes() == null || charge.lineIndexes().isEmpty())
                ? IntStream.range(0, lines.size()).boxed().toList()
                : charge.lineIndexes().stream().distinct().sorted().toList();

        Map<Integer, BigDecimal> shares = new LinkedHashMap<>();
        if (targets.size() == 1) {
            shares.put(targets.get(0), charge.cust());
            return shares;
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        for (int i : targets) {
            totalWeight = totalWeight.add(purchaseValue(lines.get(i)));
        }
        boolean byValue = totalWeight.signum() > 0;
        if (!byValue) {
            for (int i : targets) {
                totalWeight = totalWeight.add(lines.get(i).quantity());
            }
        }

        BigDecimal given = BigDecimal.ZERO;
        for (int k = 0; k < targets.size(); k++) {
            int i = targets.get(k);
            BigDecimal share;
            if (k == targets.size() - 1) {
                share = charge.cust().subtract(given);
            } else {
                BigDecimal weight = byValue ? purchaseValue(lines.get(i)) : lines.get(i).quantity();
                share = charge.cust().multiply(weight).divide(totalWeight, 2, RoundingMode.HALF_UP);
            }
            given = given.add(share);
            shares.put(i, share);
        }
        return shares;
    }

    /** What a line costs on the invoice: price as received x quantity as received. Zero when unpriced. */
    private static BigDecimal purchaseValue(DeliveryLineRequest line) {
        return line.unitCost() == null ? BigDecimal.ZERO : line.unitCost().multiply(line.quantity());
    }

    // ===================================================================================
    //  Checks, before anything is booked
    // ===================================================================================

    private static void validate(FullDeliveryRequest request) {
        List<DeliveryLineRequest> lines = request.lines();
        int n = lines.size();
        TransformationRequest transformation = request.transformation();

        for (SupplyChargeRequest charge : orEmpty(request.charges())) {
            boolean noTarget = charge.lineIndexes() == null || charge.lineIndexes().isEmpty();
            if (!charge.general() && noTarget) {
                throw invalid("La charge \u00ab " + charge.raison() + " \u00bb doit viser un produit ou \u00eatre g\u00e9n\u00e9rale.");
            }
            if (!noTarget) {
                for (Integer index : charge.lineIndexes()) {
                    checkIndex(index, n, "charge \u00ab " + charge.raison() + " \u00bb");
                }
            }
        }

        // What leaves each line, in the unit it was received in: lost on the way, or used.
        BigDecimal[] leaving = zeros(n);
        for (LossRequest loss : orEmpty(request.losses())) {
            checkIndex(loss.lineIndex(), n, "perte");
            if (Quantities.tooPrecise(loss.quantity())) {
                throw invalid("Une quantit\u00e9 se saisit au mill\u00e8me au plus (" + loss.quantity() + ").");
            }
            if (loss.reason() == LossReasonCode.PROCESS) {
                if (transformation == null) {
                    throw invalid("Une perte de fabrication suppose une transformation.");
                }
            } else {
                leaving[loss.lineIndex()] = leaving[loss.lineIndex()].add(loss.quantity());
            }
        }
        if (transformation != null) {
            for (TransformationInput input : transformation.inputs()) {
                checkIndex(input.lineIndex(), n, "mati\u00e8re utilis\u00e9e");
                leaving[input.lineIndex()] = leaving[input.lineIndex()].add(input.quantity());
            }
        }
        for (int i = 0; i < n; i++) {
            if (leaving[i].compareTo(lines.get(i).quantity()) > 0) {
                throw invalid("Ligne " + (i + 1) + " : la quantit\u00e9 perdue et utilis\u00e9e d\u00e9passe la quantit\u00e9 re\u00e7ue.");
            }
        }

        for (SalePriceRequest price : orEmpty(request.salePrices())) {
            if (price.source() == SalePriceSource.LINE) {
                checkIndex(price.index(), n, "prix de vente");
            } else if (transformation == null
                    || transformation.outputs().stream().noneMatch(o -> o.index().equals(price.index()))) {
                throw invalid("Un prix de vente vise un produit obtenu qui n'existe pas (index " + price.index() + ").");
            }
        }
    }

    private static void checkIndex(Integer index, int size, String what) {
        if (index == null || index < 0 || index >= size) {
            throw invalid("La " + what + " vise une ligne qui n'existe pas (index " + index + ").");
        }
    }

    private static BusinessRuleException invalid(String message) {
        return new BusinessRuleException("INVALID_DELIVERY", message);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] array = new BigDecimal[n];
        Arrays.fill(array, BigDecimal.ZERO);
        return array;
    }

    private static CreateSupplyRequest asSupply(Long supplierId, DeliveryLineRequest line) {
        return new CreateSupplyRequest(line.productId(), supplierId, line.packagingId(), line.quantity(),
                line.unitCost());
    }
}
