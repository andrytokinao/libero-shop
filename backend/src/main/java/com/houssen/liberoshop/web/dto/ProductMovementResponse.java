package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.AdjustmentReason;
import com.houssen.liberoshop.entity.StockAdjustment;
import com.houssen.liberoshop.entity.StockMovement;
import com.houssen.liberoshop.entity.StockOutput;
import com.houssen.liberoshop.entity.Supply;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line of a product's stock history, whatever kind of movement it was.
 *
 * @param type          SUPPLY, OUTPUT or ADJUSTMENT
 * @param quantity      what the stock moved by, in base units and signed: +20 received, -3 sold
 * @param performedBy   the full name of who booked it
 * @param supplierName  on a SUPPLY, who delivered; null when no supplier was named
 * @param unitCost      on a SUPPLY, what one base unit cost, or null when it was not given
 * @param invoiceNumber on an OUTPUT, the invoice the goods left against
 * @param reason        on an ADJUSTMENT, why
 * @param comment       on an ADJUSTMENT, what the operator had to say
 */
public record ProductMovementResponse(Long id,
                                      String type,
                                      BigDecimal quantity,
                                      LocalDateTime movementDate,
                                      String performedBy,
                                      String supplierName,
                                      BigDecimal unitCost,
                                      String invoiceNumber,
                                      AdjustmentReason reason,
                                      String comment) {

    public static ProductMovementResponse of(StockMovement movement) {
        String author = movement.getPerformedBy().getFullName();
        return switch (movement) {
            case Supply supply -> new ProductMovementResponse(supply.getId(), "SUPPLY",
                    supply.getQuantity(), supply.getMovementDate(), author,
                    supply.getSupplier() == null ? null : supply.getSupplier().getName(),
                    supply.baseUnitCost(), null, null, null);
            case StockOutput output -> new ProductMovementResponse(output.getId(), "OUTPUT",
                    output.getQuantity().negate(), output.getMovementDate(), author,
                    null, null, output.getInvoice().getInvoiceNumber(), null, null);
            case StockAdjustment adjustment -> new ProductMovementResponse(adjustment.getId(), "ADJUSTMENT",
                    adjustment.getQuantity(), adjustment.getMovementDate(), author,
                    null, null, null, adjustment.getReason(), adjustment.getComment());
            default -> throw new IllegalStateException(
                    "Mouvement de stock de type inconnu : " + movement.getClass().getSimpleName());
        };
    }

    /**
     * One page of the history, newest first.
     *
     * @param total how many movements the product has in all
     */
    public record Page(java.util.List<ProductMovementResponse> items, long total, int page, int size,
                       boolean hasMore) {
    }
}
