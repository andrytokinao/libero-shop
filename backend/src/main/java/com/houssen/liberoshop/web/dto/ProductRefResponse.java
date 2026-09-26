package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;

import java.math.BigDecimal;

/**
 * The catalogue product an import line was matched with, as much of it as the preview needs
 * to justify the match on screen.
 *
 * <p>Deliberately not the full {@link ProductResponse}: a preview of 500 lines would carry
 * 500 of those, and the stock value and alert flag say nothing about whether this is the same
 * product as the one in the file.
 *
 * @param stockQuantity what is on the shelf now, so the row can show "40 + 12 = 52"
 */
public record ProductRefResponse(Long id,
                                 String name,
                                 int stockQuantity,
                                 String unit,
                                 BigDecimal price,
                                 String barcode,
                                 String categoryPath) {

    public static ProductRefResponse of(Product product, String categoryPath) {
        return new ProductRefResponse(
                product.getId(),
                product.getName(),
                product.getStockQuantity(),
                product.getUnit(),
                product.getPrice(),
                product.getBarcode(),
                categoryPath);
    }
}
