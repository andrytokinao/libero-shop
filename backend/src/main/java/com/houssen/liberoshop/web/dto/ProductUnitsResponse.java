package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.web.dto.ProductResponse.Packaging;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every way a product is sold: its base unit, then the other units, smallest first.
 *
 * <p>Every write of the units dialog answers with this whole picture rather than the one row it
 * touched, so the screen re-renders from what the server holds and never has to merge.
 *
 * @param baseUnit       null for a product counted in bare units
 * @param category       the rayon it is filed under, or null for none
 * @param purchaseUnitId the packaging the shop buys it in, or null for the base unit. Which unit,
 *                       not at what price: the price is served by the costing API alone.
 */
public record ProductUnitsResponse(Long productId,
                                   String productName,
                                   String baseUnit,
                                   BigDecimal basePrice,
                                   String baseBarcode,
                                   BigDecimal stockQuantity,
                                   CategoryResponse category,
                                   List<Packaging> packagings,
                                   Long purchaseUnitId) {

    public static ProductUnitsResponse of(Product product, List<ProductPackaging> packagings) {
        return new ProductUnitsResponse(
                product.getId(),
                product.getName(),
                product.getUnit(),
                product.getPrice(),
                product.getBarcode(),
                product.getStockQuantity(),
                CategoryResponse.of(product.getCategory()),
                packagings.stream().map(Packaging::of).toList(),
                product.getPurchaseUnit() == null ? null : product.getPurchaseUnit().getId());
    }
}
