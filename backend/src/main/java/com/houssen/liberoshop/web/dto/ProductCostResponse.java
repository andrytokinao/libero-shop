package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What one product costs the shop, beside what it sells for.
 *
 * <p>Served apart from {@link ProductResponse} on purpose: that one is read by every role, and
 * the purchase price is not the counter's business.
 *
 * @param averageCost      the weighted average cost of one base unit, to the cent, or null while
 *                         no receipt has stated one
 * @param unitMargin       sale price minus average cost, or null with it
 * @param marginRate       {@code unitMargin} as a percentage of the sale price, or null
 * @param lastUnitCost     what the latest costed receipt paid per base unit
 * @param lastSupplierName who that receipt came from; null for an import entry or none at all
 * @param purchaseUnitId   the packaging the shop buys it in, or null for the base unit
 * @param units            the base unit, then every packaging, each with its own sale price,
 *                         cost and profit -- worked out here from the exact average, so a tonne
 *                         reads back at the price it was typed at
 */
public record ProductCostResponse(Long productId,
                                  String name,
                                  String unit,
                                  BigDecimal salePrice,
                                  BigDecimal stockQuantity,
                                  BigDecimal averageCost,
                                  BigDecimal unitMargin,
                                  BigDecimal marginRate,
                                  BigDecimal lastUnitCost,
                                  String lastSupplierName,
                                  LocalDateTime lastPurchaseDate,
                                  Long purchaseUnitId,
                                  List<UnitCost> units) {

    /**
     * One unit of the product, priced both ways: "sac 50 kg — vendu 150 000, coûte 137 500,
     * bénéfice 12 500 (8,3 %)".
     *
     * @param packagingId null for the base unit
     * @param label       the unit's name; null for a base unit counted in bare units
     * @param factor      base units in one of it; 1 for the base unit
     * @param cost        what one of it cost, at the average: null while none is known
     * @param margin      salePrice − cost, or null with it
     * @param marginRate  margin as a percentage of the sale price, one decimal, or null
     * @param lastCost    what one of it cost at the latest costed receipt, for pre-filling the
     *                    next one; null while none
     */
    public record UnitCost(Long packagingId,
                           String label,
                           BigDecimal factor,
                           BigDecimal salePrice,
                           BigDecimal cost,
                           BigDecimal margin,
                           BigDecimal marginRate,
                           BigDecimal lastCost) {
    }
}
