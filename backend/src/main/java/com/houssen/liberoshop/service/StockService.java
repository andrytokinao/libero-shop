package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.AdjustmentReason;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.StockAdjustment;
import com.houssen.liberoshop.entity.StockMovement;
import com.houssen.liberoshop.entity.StockOutput;
import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.util.Quantities;
import com.houssen.liberoshop.web.dto.CreateStockAdjustmentRequest;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import com.houssen.liberoshop.web.dto.ProductMovementResponse;
import com.houssen.liberoshop.web.dto.StockAdjustmentResponse;
import com.houssen.liberoshop.web.dto.StockOutputPageResponse;
import com.houssen.liberoshop.web.dto.StockOutputResponse;
import com.houssen.liberoshop.web.dto.SupplyResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Stock movements: goods in (SUPPLY), goods out (OUTPUT) and corrections by hand (ADJUSTMENT).
 *
 * <p>Outputs are never written here -- they are produced by {@link SaleService} as part of
 * the checkout transaction. This service only reads them, which keeps a single place able
 * to take stock out and makes the ledger match the invoices by construction.
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final BusinessCalendar calendar;
    private final ApplicationEventPublisher events;

    public StockService(StockMovementRepository movements, ProductRepository products,
                        SupplierRepository suppliers, BusinessCalendar calendar,
                        ApplicationEventPublisher events) {
        this.movements = movements;
        this.products = products;
        this.suppliers = suppliers;
        this.calendar = calendar;
        this.events = events;
    }

    public List<SupplyResponse> findSupplies() {
        return movements.findSupplies().stream().map(SupplyResponse::of).toList();
    }

    /**
     * One page of the outputs, newest first, with what all the matching ones add up to.
     *
     * @param search free text on product name, invoice number and client; case ignored
     * @param page   zero-based
     * @param size   clamped to {@code 1..MAX_PAGE_SIZE}
     */
    public StockOutputPageResponse findOutputs(String search, int page, int size) {
        String needle = likePattern(search);
        Page<StockOutput> found = movements.findOutputs(needle,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
        Object[] sums = movements.sumOutputs(needle).getFirst();
        return new StockOutputPageResponse(
                found.getContent().stream().map(StockOutputResponse::of).toList(),
                found.getTotalElements(),
                Quantities.scaled((BigDecimal) sums[0]),
                ((BigDecimal) sums[1]).setScale(2, RoundingMode.HALF_UP),
                found.getNumber(), found.getSize(), found.hasNext());
    }

    /**
     * The words typed, as a {@code like} pattern matching them anywhere: lower-cased, with the
     * characters {@code like} gives a meaning to escaped, so a search for "50%" finds "50%" and
     * not everything. Null when nothing was typed.
     */
    static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * Receives goods: raises the stock, blends their cost into the product's average, and
     * records the movement that explains both.
     *
     * <p>Booked in the unit they came in -- "10 sacs at 145 000" -- and kept that way on the
     * receipt, so it reads like the supplier's invoice. The stock and the average move in base
     * units: 1 750 kapoka at 828.57.
     */
    @RequiresActiveLicense
    @Transactional
    public SupplyResponse registerSupply(CreateSupplyRequest request, UserApp receiver) {
        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Produit", request.productId()));
        // No supplier named is allowed: the entry is kept all the same, only nobody is credited.
        Supplier supplier = request.supplierId() == null ? null
                : suppliers.findById(request.supplierId())
                        .orElseThrow(() -> ResourceNotFoundException.of("Fournisseur", request.supplierId()));
        if (Quantities.tooPrecise(request.quantity())) {
            throw new BusinessRuleException("QUANTITY_TOO_PRECISE",
                    "Une quantite se saisit au millieme au plus (" + request.quantity() + ").");
        }
        // The same resolution as a sale's: the base unit, or one of the product's packagings.
        SaleUnit unit = SaleUnit.of(product, request.packagingId());
        boolean inBaseUnit = request.packagingId() == null;

        Supply supply = Supply.builder()
                .quantity(Quantities.scaled(request.quantity().multiply(unit.factor())))
                .movementDate(calendar.now())
                .product(product)
                .performedBy(receiver)
                .supplier(supplier)
                .unitCost(PurchaseCosting.scaled(request.unitCost()))
                .unitLabel(inBaseUnit ? null : unit.label())
                .unitFactor(inBaseUnit ? null : unit.factor())
                .build();
        // The average is kept per base unit: ten sacks at 145 000 teach it 828.571429 a kapoka.
        PurchaseCosting.receive(product, supply.getQuantity(), supply.exactBaseUnitCost());
        movements.save(supply);

        events.publishEvent(new StockChangedEvent(List.of(product.getId())));
        return SupplyResponse.of(supply);
    }

    /**
     * One page of everything that moved one product's stock, newest first.
     *
     * @param page zero-based
     * @param size clamped to {@code 1..MAX_PAGE_SIZE}
     */
    public ProductMovementResponse.Page findMovementsOf(Long productId, int page, int size) {
        if (!products.existsById(productId)) {
            throw ResourceNotFoundException.of("Produit", productId);
        }
        Page<StockMovement> found = movements.findByProduct(productId,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
        return new ProductMovementResponse.Page(
                found.getContent().stream().map(ProductMovementResponse::of).toList(),
                found.getTotalElements(), found.getNumber(), found.getSize(), found.hasNext());
    }

    public List<StockAdjustmentResponse> findAdjustments() {
        return movements.findAdjustments().stream().map(StockAdjustmentResponse::of).toList();
    }

    /**
     * Corrects the stock by hand and records why: the only way it moves without a receipt or
     * a sale behind it, which is exactly why each correction is kept, signed and named.
     *
     * <p>A loss is said as what left -- "3 cassees" -- and refused beyond what the stock holds.
     * A count is said as what is on the shelf -- "il en reste 37" -- and the difference is
     * worked out here, on the locked row, so a sale rung up while the shelf was being counted
     * is not counted twice.
     */
    @RequiresActiveLicense
    @Transactional
    public StockAdjustmentResponse registerAdjustment(CreateStockAdjustmentRequest request, UserApp author) {
        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Produit", request.productId()));
        if (Quantities.tooPrecise(request.quantity())) {
            throw new BusinessRuleException("QUANTITY_TOO_PRECISE",
                    "Une quantite se saisit au millieme au plus (" + request.quantity() + ").");
        }
        String comment = PackagingRules.squeeze(request.comment());
        if (request.reason() == AdjustmentReason.OTHER && comment == null) {
            throw new BusinessRuleException("ADJUSTMENT_COMMENT_REQUIRED",
                    "Precisez en commentaire la raison de cette correction.");
        }

        StockAdjustment adjustment = StockAdjustment.builder()
                .quantity(Quantities.scaled(differenceOf(request, product)))
                .movementDate(calendar.now())
                .product(product)
                .performedBy(author)
                .reason(request.reason())
                .comment(comment)
                .build();
        product.adjustStock(adjustment.getQuantity());
        movements.save(adjustment);

        events.publishEvent(new StockChangedEvent(List.of(product.getId())));
        return StockAdjustmentResponse.of(adjustment);
    }

    /** What the stock moves by: minus what was lost, or the count minus what the stock says. */
    private static BigDecimal differenceOf(CreateStockAdjustmentRequest request, Product product) {
        BigDecimal stock = product.getStockQuantity();
        if (request.reason().isCount()) {
            BigDecimal difference = request.quantity().subtract(stock);
            if (difference.signum() == 0) {
                throw new BusinessRuleException("ADJUSTMENT_NOTHING_TO_CORRECT",
                        "Le stock de \"" + product.getName() + "\" est deja de " + plain(stock)
                                + " : il n'y a rien a corriger.");
            }
            return difference;
        }
        if (request.quantity().signum() <= 0) {
            throw new BusinessRuleException("ADJUSTMENT_QUANTITY_INVALID",
                    "La quantite perdue doit etre superieure a zero.");
        }
        if (request.quantity().compareTo(stock) > 0) {
            throw new BusinessRuleException("ADJUSTMENT_EXCEEDS_STOCK",
                    "Le stock de \"" + product.getName() + "\" n'est que de " + plain(stock)
                            + " : impossible d'en retirer " + plain(request.quantity())
                            + ". Si le rayon a ete compte, saisissez un inventaire.");
        }
        return request.quantity().negate();
    }

    private static String plain(BigDecimal quantity) {
        return quantity.signum() == 0 ? "0" : quantity.stripTrailingZeros().toPlainString();
    }

    public long countSuppliesLastWeek() {
        return movements.countSuppliesSince(calendar.startOfDaysAgo(7));
    }

    public BigDecimal unitsSuppliedLastWeek() {
        return movements.sumSuppliedUnitsSince(calendar.startOfDaysAgo(7));
    }
}
