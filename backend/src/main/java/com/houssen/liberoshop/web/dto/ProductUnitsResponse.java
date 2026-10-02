package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every way a product is sold: its base unit, then the other units, smallest first.
 *
 * <p>Every write of the units dialog answers with this whole picture rather than the one row it
 * touched, so the screen re-renders from what the server holds and never has to merge.
 *
 * @param baseUnit null for a product counted in bare units
 */
public record ProductUnitsResponse(Long productId,
                                   String productName,
                                   String baseUnit,
                                   BigDecimal basePrice,
                                   String baseBarcode,
                                   int stockQuantity,
                                   List<Packaging> packagings) {

    public static ProductUnitsResponse of(Product product, List<ProductPackaging> packagings) {
        return new ProductUnitsResponse(
                product.getId(),
                product.getName(),
                product.getUnit(),
                product.getPrice(),
                product.getBarcode(),
                product.getStockQuantity(),
                packagings.stream().map(Packaging::of).toList());
    }

    /** @param factor base units in one of these */
    public record Packaging(Long id, String label, BigDecimal factor, BigDecimal price, String barcode) {

        static Packaging of(ProductPackaging packaging) {
            return new Packaging(
                    packaging.getId(),
                    packaging.getLabel(),
                    packaging.getFactor(),
                    packaging.getPrice(),
                    packaging.getBarcode());
        }
    }
}
