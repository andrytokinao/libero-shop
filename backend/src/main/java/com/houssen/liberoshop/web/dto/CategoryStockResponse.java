package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;

/**
 * What one rayon is worth, for the stock screens.
 *
 * <p>Rows are per rayon, not rolled up into their parent: "Boissons" counts the goods filed
 * directly under it, and "Boissons &gt; Eau" counts its own. A rolled-up figure would double-count
 * against the shop's total, and the screens that use this are asked "where is the money sitting",
 * which is a question about the shelf a product is actually on.
 *
 * @param path the rayon with its ancestors, since "Eau" alone does not say which branch it is
 *             read from once the catalogue has a tree
 */
public record CategoryStockResponse(CategoryResponse category,
                                    String path,
                                    long references,
                                    BigDecimal units,
                                    BigDecimal value,
                                    int sharePercent) {
}
