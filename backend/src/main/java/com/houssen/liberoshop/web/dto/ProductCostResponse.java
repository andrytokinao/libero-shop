package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What one product costs the shop, beside what it sells for.
 *
 * <p>Served apart from {@link ProductResponse} on purpose: that one is read by every role, and
 * the purchase price is not the counter's business.
 *
 * @param averageCost      the weighted average cost of the stock on hand, or null while no
 *                         receipt has stated one
 * @param unitMargin       sale price minus average cost, or null with it
 * @param marginRate       {@code unitMargin} as a percentage of the sale price, or null
 * @param lastUnitCost     what the latest costed receipt paid, to pre-fill the next one
 * @param lastSupplierName who that receipt came from; null for an import entry or none at all
 */
public record ProductCostResponse(Long productId,
                                  String name,
                                  String unit,
                                  BigDecimal salePrice,
                                  int stockQuantity,
                                  BigDecimal averageCost,
                                  BigDecimal unitMargin,
                                  BigDecimal marginRate,
                                  BigDecimal lastUnitCost,
                                  String lastSupplierName,
                                  LocalDateTime lastPurchaseDate) {
}
