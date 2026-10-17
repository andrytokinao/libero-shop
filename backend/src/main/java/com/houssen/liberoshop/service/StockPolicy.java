package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;

import java.math.BigDecimal;

/**
 * Where "the stock is low" is defined, once.
 *
 * <p>Each product may have its own threshold, in base units; the others share
 * {@link #LOW_STOCK_THRESHOLD}. The dashboards, the stock list and the database query all go
 * through here, so they agree on which products are in alert.
 */
public final class StockPolicy {

    public static final BigDecimal LOW_STOCK_THRESHOLD = BigDecimal.TEN;

    private StockPolicy() {
    }

    /** The product's own threshold, or the shop's common one. */
    public static BigDecimal thresholdOf(Product product) {
        return product.getLowStockThreshold() != null ? product.getLowStockThreshold() : LOW_STOCK_THRESHOLD;
    }

    public static boolean isLowStock(Product product) {
        return product.getStockQuantity().compareTo(thresholdOf(product)) < 0;
    }
}
