package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.AdjustmentReason;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.StockAdjustment;
import com.houssen.liberoshop.entity.StockMovement;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.util.Quantities;
import com.houssen.liberoshop.web.dto.StockAdjustmentResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Undoing a receipt or a correction entered by mistake -- without deleting it.
 *
 * <p>The movement stays in the product's history, marked cancelled, and an opposite correction
 * (reason {@link AdjustmentReason#CANCELLATION}) puts the stock back: whoever reads the history
 * later sees both, and why. A movement is cancelled once; a cancellation that would take the
 * stock below zero -- the goods already sold -- is refused.
 *
 * <p>The average purchase cost is left as it is: the receipt's price was blended into it, and
 * taking it back out would need every receipt since. The stock, which is what was wrong, is put
 * right.
 */
@Service
@Transactional(readOnly = true)
public class MovementCancellationService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final BusinessCalendar calendar;
    private final ApplicationEventPublisher events;

    public MovementCancellationService(StockMovementRepository movements, ProductRepository products,
                                       BusinessCalendar calendar, ApplicationEventPublisher events) {
        this.movements = movements;
        this.products = products;
        this.calendar = calendar;
        this.events = events;
    }

    /** A receipt cancelled: what came in goes back out. */
    @RequiresActiveLicense
    @Transactional
    public StockAdjustmentResponse cancelSupply(Long supplyId, UserApp author) {
        Supply supply = movements.findById(supplyId)
                .filter(Supply.class::isInstance).map(Supply.class::cast)
                .orElseThrow(() -> ResourceNotFoundException.of("Reception", supplyId));
        return cancel(supply, "Annulation de la reception du " + DAY.format(supply.getMovementDate()), author);
    }

    /**
     * A whole delivery note cancelled: every receipt or none, like the note was booked. The
     * products are locked in id order, as {@link DeliveryService} does, so two notes never wait
     * on each other.
     */
    @RequiresActiveLicense
    @Transactional
    public List<StockAdjustmentResponse> cancelDelivery(List<Long> supplyIds, UserApp author) {
        List<Supply> supplies = supplyIds.stream().distinct()
                .map(id -> movements.findById(id)
                        .filter(Supply.class::isInstance).map(Supply.class::cast)
                        .orElseThrow(() -> ResourceNotFoundException.of("Reception", id)))
                .sorted(Comparator.comparing((Supply supply) -> supply.getProduct().getId()))
                .toList();
        return supplies.stream()
                .map(supply -> cancel(supply,
                        "Annulation de la reception du " + DAY.format(supply.getMovementDate()), author))
                .toList();
    }

    /** A correction cancelled: the stock moves back by what it moved. */
    @RequiresActiveLicense
    @Transactional
    public StockAdjustmentResponse cancelAdjustment(Long adjustmentId, UserApp author) {
        StockAdjustment adjustment = movements.findById(adjustmentId)
                .filter(StockAdjustment.class::isInstance).map(StockAdjustment.class::cast)
                .orElseThrow(() -> ResourceNotFoundException.of("Correction", adjustmentId));
        if (adjustment.getReason() == AdjustmentReason.CANCELLATION) {
            throw new BusinessRuleException("CANCELLATION_FINAL",
                    "Une annulation ne s'annule pas : saisissez une nouvelle correction.");
        }
        return cancel(adjustment, "Annulation de la correction du " + DAY.format(adjustment.getMovementDate()), author);
    }

    private StockAdjustmentResponse cancel(StockMovement movement, String comment, UserApp author) {
        if (movement.getCancelledAt() != null) {
            throw new BusinessRuleException("MOVEMENT_ALREADY_CANCELLED", "Cette saisie est deja annulee.");
        }
        Product product = products.findByIdForUpdate(movement.getProduct().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Produit", movement.getProduct().getId()));
        BigDecimal back = movement.getQuantity().negate();
        BigDecimal after = product.getStockQuantity().add(back);
        if (after.signum() < 0) {
            throw new BusinessRuleException("CANCELLATION_EXCEEDS_STOCK",
                    "Le stock de \"" + product.getName() + "\" n'est plus que de "
                            + Quantities.format(product.getStockQuantity())
                            + " : une partie a deja ete vendue. Corrigez plutot le stock par un inventaire.");
        }
        StockAdjustment opposite = StockAdjustment.builder()
                .quantity(Quantities.scaled(back))
                .movementDate(calendar.now())
                .product(product)
                .performedBy(author)
                .reason(AdjustmentReason.CANCELLATION)
                .comment(comment)
                .build();
        product.adjustStock(back);
        movement.setCancelledAt(calendar.now());
        movements.save(opposite);

        events.publishEvent(new StockChangedEvent(List.of(product.getId())));
        return StockAdjustmentResponse.of(opposite);
    }
}
