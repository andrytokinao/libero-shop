package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.AdjustmentReason;
import com.houssen.liberoshop.entity.StockAdjustment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * An ADJUSTMENT row of the stock_movement table: the stock corrected by hand.
 *
 * @param quantity  what the stock moved by, in base units and signed: -3 for three bottles
 *                  broken, +2 when a count found two more than expected
 * @param costValue {@code quantity} at the product's average purchase cost -- what a loss cost
 *                  the shop -- signed like it, or null while the product was never costed
 */
public record StockAdjustmentResponse(Long id,
                                      BigDecimal quantity,
                                      LocalDateTime movementDate,
                                      ProductResponse product,
                                      UserResponse performedBy,
                                      AdjustmentReason reason,
                                      String comment,
                                      BigDecimal costValue,
                                      LocalDateTime cancelledAt) {

    public static StockAdjustmentResponse of(StockAdjustment adjustment) {
        BigDecimal cost = adjustment.getProduct().getAverageCost();
        return new StockAdjustmentResponse(
                adjustment.getId(),
                adjustment.getQuantity(),
                adjustment.getMovementDate(),
                ProductResponse.withoutUnits(adjustment.getProduct()),
                UserResponse.of(adjustment.getPerformedBy()),
                adjustment.getReason(),
                adjustment.getComment(),
                cost == null ? null
                        : cost.multiply(adjustment.getQuantity()).setScale(2, RoundingMode.HALF_UP),
                adjustment.getCancelledAt());
    }
}
