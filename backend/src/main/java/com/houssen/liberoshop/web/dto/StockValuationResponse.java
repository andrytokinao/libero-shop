package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the stock on hand is worth, now: what it cost to bring in, what it would bring if sold,
 * and the profit that is waiting in it.
 *
 * <p>A snapshot, not a period: it describes the shelves at the moment it is asked, and so does
 * not move with the dates of the margin report beside it.
 *
 * @param costValue           units × weighted average cost, over the costed products -- the money
 *                            tied up in the stock
 * @param saleValue           units × sale price, over every product -- what the stock would bring
 *                            if it all sold at today's prices
 * @param costedSaleValue     the part of {@code saleValue} whose cost is known
 * @param potentialMargin     {@code costedSaleValue - costValue}: the profit still on the shelves
 * @param potentialMarginRate {@code potentialMargin} as a percentage of {@code costedSaleValue}
 * @param uncostedUnits       units of products that have never been costed
 * @param coveragePercent     share of {@code saleValue} the cost figures speak for, 0 to 100
 * @param byCategory          one row per rayon holding stock, largest tied-up money first
 */
public record StockValuationResponse(int references,
                                     BigDecimal units,
                                     BigDecimal costValue,
                                     BigDecimal saleValue,
                                     BigDecimal costedSaleValue,
                                     BigDecimal potentialMargin,
                                     BigDecimal potentialMarginRate,
                                     BigDecimal uncostedUnits,
                                     int coveragePercent,
                                     List<CategoryValuation> byCategory) {

    /**
     * @param categoryId    null for the products filed under no rayon
     * @param uncostedUnits units of its products that have never been costed; while above
     *                      zero, {@code potentialMargin} covers only the others
     */
    public record CategoryValuation(Long categoryId,
                                    String path,
                                    int references,
                                    BigDecimal units,
                                    BigDecimal costValue,
                                    BigDecimal saleValue,
                                    BigDecimal potentialMargin,
                                    BigDecimal potentialMarginRate,
                                    BigDecimal uncostedUnits) {
    }
}
