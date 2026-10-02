package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Gross margin over a period: what was sold, what it cost, what is left.
 *
 * <p>The margin is computed only on lines whose cost was known when they were sold. The rest is
 * not guessed at: it is reported as {@code uncostedRevenue}, so a screen can say "the margin
 * covers 92% of sales" instead of presenting goods of unknown cost as pure profit.
 *
 * @param from            first day, inclusive
 * @param to              last day, inclusive
 * @param revenue         everything sold in the period, paid or not -- the turnover
 * @param paidRevenue     the part of {@code revenue} already settled; the rest is owed
 * @param costedRevenue   the part of {@code revenue} whose cost is known
 * @param costOfGoodsSold what the costed lines cost the shop
 * @param grossMargin     {@code costedRevenue - costOfGoodsSold}
 * @param marginRate      {@code grossMargin} as a percentage of {@code costedRevenue}, or null
 * @param uncostedRevenue {@code revenue - costedRevenue}
 * @param coveragePercent share of {@code revenue} the margin speaks for, 0 to 100
 * @param byProduct       one row per product sold, largest margin first
 */
public record MarginReportResponse(LocalDate from,
                                   LocalDate to,
                                   BigDecimal revenue,
                                   BigDecimal paidRevenue,
                                   BigDecimal costedRevenue,
                                   BigDecimal costOfGoodsSold,
                                   BigDecimal grossMargin,
                                   BigDecimal marginRate,
                                   BigDecimal uncostedRevenue,
                                   int coveragePercent,
                                   List<ProductMargin> byProduct) {

    /**
     * @param fullyCosted false when some of this product's lines were sold without a known cost;
     *                    its margin then covers only the others
     */
    public record ProductMargin(Long productId,
                                String name,
                                BigDecimal unitsSold,
                                BigDecimal revenue,
                                BigDecimal costOfGoodsSold,
                                BigDecimal grossMargin,
                                BigDecimal marginRate,
                                boolean fullyCosted) {
    }
}
