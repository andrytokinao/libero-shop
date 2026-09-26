package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.service.StockPolicy;

import java.math.BigDecimal;

/**
 * @param lowStock decided server-side so the alert threshold has a single definition;
 *                 the UI only has to style what it is told
 */
public record ProductResponse(Long id,
                              String name,
                              BigDecimal price,
                              int stockQuantity,
                              String barcode,
                              CategoryResponse category,
                              boolean lowStock,
                              BigDecimal stockValue) {

    public static ProductResponse of(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getBarcode(),
                CategoryResponse.of(product.getCategory()),
                StockPolicy.isLowStock(product),
                product.getPrice().multiply(BigDecimal.valueOf(product.getStockQuantity())));
    }
}
