package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What one supplier has charged for one product, over every costed receipt.
 *
 * @param supplierId   null for the entries an import wrote without a supplier, grouped as one
 *                     row so their units are not lost from the comparison
 * @param averageCost  weighted by the units of each receipt, so one large delivery weighs more
 *                     than a small top-up
 * @param lastUnitCost the latest price, which is the one the next order will most likely pay
 */
public record SupplierPriceResponse(Long supplierId,
                                    String supplierName,
                                    int receipts,
                                    BigDecimal units,
                                    BigDecimal averageCost,
                                    BigDecimal lowestCost,
                                    BigDecimal highestCost,
                                    BigDecimal lastUnitCost,
                                    LocalDateTime lastPurchaseDate) {
}
