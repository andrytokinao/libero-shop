package com.houssen.liberoshop.service.delivery;

import com.houssen.liberoshop.license.RequiresActiveLicense; // A ADAPTER : package de votre annotation
import com.houssen.liberoshop.entity.*;
import com.houssen.liberoshop.service.StockChangedEvent;
import com.houssen.liberoshop.service.StockService;
import com.houssen.liberoshop.service.SupplyChargeMapper;
import com.houssen.liberoshop.service.SupplyCostService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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
    private final SupplyCostService supplyCosts;
    private final StockMovementRepository movements;
    private final ApplicationEventPublisher events;

    public DeliveryService(StockService stock,
                           TransformationService transformations,
                           DeliveryNoteRepository notes,
                           SupplyCostService supplyCosts,
                           StockMovementRepository movements,
                           ApplicationEventPublisher events) {
        this.stock = stock;
        this.transformations = transformations;
        this.notes = notes;
        this.supplyCosts = supplyCosts;
        this.movements = movements;
        this.events = events;
    }

    /** @return the receipts, in the order of the note's lines */

    @RequiresActiveLicense
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
        return CreateSupplyRequest.builder()
                .productId(line.productId())
                .supplierId(supplierId)
                .packagingId(line.packagingId())
                .quantity(line.quantity())
                .unitCost(line.unitCost())
                .build();
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
    @RequiresActiveLicense
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

        // 2. What each line carries on top of its price, and what it loses on the way. The shares
        //    are needed to book the receipts (average cost): computed first on drafts, never saved,
        //    by the same charge classes as the real ones.
        Supply[] drafts = drafts(lines);
        buildCharges(charges, drafts).forEach(SupplyCharge::allocate);

        BigDecimal[] extra = Arrays.stream(drafts)
                .map(Supply::chargesTotal)
                .toArray(BigDecimal[]::new);
        BigDecimal[] lost = zeros(n);
        lineLosses.forEach(l -> lost[l.lineIndex()] = lost[l.lineIndex()].add(l.quantity()));

        // 3. The receipts, locked in the order of the product ids.
        Supply[] booked = new Supply[n];
        IntStream.range(0, n).boxed()
                .sorted(Comparator.comparing(i -> lines.get(i).productId()))
                .forEach(i -> booked[i] = stock.bookSupply(
                        asSupply(request.supplierId(), lines.get(i)), receiver, extra[i], lost[i], note));

        note.setSupplier(booked[0].getSupplier());
        note.setDate(booked[0].getMovementDate());

        // 4. The charges linked to the receipts, split, and the entry cost stored on each receipt.
        List<SupplyCharge> bookedCharges = buildCharges(charges, booked);
        bookedCharges.forEach(charge -> charge.setDeliveryNote(note));
        SupplyCostService.EntryCostSummary summary =
                supplyCosts.applyCharges(Arrays.asList(booked), bookedCharges);
        note.getCharges().addAll(bookedCharges);
        note.setPurchaseTotal(summary.purchaseTotal());
        note.setChargesTotal(summary.chargesTotal());

        // 5. The losses on the way: quantity only, their cost is carried by what is left.
        for (LossRequest loss : lineLosses) {
            Supply receipt = booked[loss.lineIndex()];
            StockLoss saved = movements.save(StockLoss.builder()
                    .quantity(Quantities.scaled(loss.quantity().multiply(receipt.factor())))
                    .movementDate(receipt.getMovementDate())
                    .product(receipt.getProduct())
                    .performedBy(receiver)
                    .reason(LossReason.valueOf(loss.reason().name()))
                    .supply(receipt)
                    .build());
            // Both sides, so the landed costs read in this transaction see the loss.
            if (receipt.getLosses() == null) {
                receipt.setLosses(new ArrayList<>());
            }
            receipt.getLosses().add(saved);
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
     * The charges of the request, each linked to the receipts it is for. How a charge is split is
     * the business of its class: see {@link SupplyCharge#allocate()}.
     *
     * @param receipts the receipts by line index
     */
    private static List<SupplyCharge> buildCharges(List<SupplyChargeRequest> requests,
                                                   Supply[] receipts) {
        return requests.stream().map(request -> {
            SupplyCharge charge = SupplyChargeMapper.toCharge(request);
            targets(request, receipts.length).forEach(i -> charge.addSupply(receipts[i]));
            return charge;
        }).toList();
    }

    /** The lines a charge is for: all of them when general, else the ones it names. */
    private static List<Integer> targets(SupplyChargeRequest charge, int lineCount) {
        return charge.general() || charge.lineIndexes() == null || charge.lineIndexes().isEmpty()
                ? IntStream.range(0, lineCount).boxed().toList()
                : charge.lineIndexes().stream().distinct().sorted().toList();
    }

    /** Unsaved receipts carrying only what a split reads: quantity and price as received. */
    private static Supply[] drafts(List<DeliveryLineRequest> lines) {
        return lines.stream()
                .map(line -> (Supply) Supply.builder()
                        .quantity(line.quantity())
                        .unitCost(line.unitCost())
                        .build())
                .toArray(Supply[]::new);
    }

    // ===================================================================================
    //  Checks, before anything is booked
    // ===================================================================================

    private static void validate(FullDeliveryRequest request) {
        List<DeliveryLineRequest> lines = request.lines();
        int n = lines.size();
        TransformationRequest transformation = request.transformation();

        for (int i = 0; i < n; i++) {
            if (lines.get(i).quantity().signum() <= 0) {
                throw invalid("Ligne " + (i + 1) + " : la quantit\u00e9 re\u00e7ue doit \u00eatre positive.");
            }
        }

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
        return CreateSupplyRequest.builder()
                .productId(line.productId())
                .supplierId(supplierId)
                .packagingId(line.packagingId())
                .quantity(line.quantity())
                .unitCost(line.unitCost())
                .build();
    }
}