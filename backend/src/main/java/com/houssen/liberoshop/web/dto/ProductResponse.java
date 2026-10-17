package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.service.StockPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * @param stockQuantity in the base unit ({@code unit}), to the thousandth
 * @param unit          how the shelf counts it, as the operator wrote it -- null for a catalogue
 *                      counted in bare units, which the UI renders as a dash rather than inventing
 *                      a word for
 * @param lowStock      decided server-side so the alert threshold has a single definition;
 *                      the UI only has to style what it is told
 * @param lowStockThreshold the threshold it is judged by, in base units: its own or the shop's
 * @param retired       no longer offered for sale; still in the stock lists and the history
 * @param packagings    the other units it sells in, smallest first -- what the till offers beside
 *                      the base unit. Empty for most products, and on the responses nested in an
 *                      invoice or a movement, which describe what moved rather than what is for sale
 */
public record ProductResponse(Long id,
                              String name,
                              BigDecimal price,
                              BigDecimal stockQuantity,
                              String unit,
                              String barcode,
                              CategoryResponse category,
                              boolean lowStock,
                              BigDecimal lowStockThreshold,
                              boolean retired,
                              BigDecimal stockValue,
                              List<Packaging> packagings) {

    /** With its units: for the catalogue and the sale screens. Needs an open session. */
    public static ProductResponse of(Product product) {
        return of(product, product.getPackagings().stream().map(Packaging::of).toList());
    }

    /**
     * Without its units: for the product nested in an invoice line, a supply or an output, where
     * they would cost a query per row to say nothing about the movement.
     */
    public static ProductResponse withoutUnits(Product product) {
        return of(product, List.of());
    }

    private static ProductResponse of(Product product, List<Packaging> packagings) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getUnit(),
                product.getBarcode(),
                CategoryResponse.of(product.getCategory()),
                StockPolicy.isLowStock(product),
                StockPolicy.thresholdOf(product),
                product.isRetired(),
                product.getPrice().multiply(product.getStockQuantity()).setScale(2, RoundingMode.HALF_UP),
                packagings);
    }

    /**
     * @param factor            base units in one of these: 87.5 for a 25 kg sack of rice counted
     *                          in kapoka -- what the stock moves by
     * @param reference         the unit it is written in -- "kg" for "sac 25 kg" -- or null for
     *                          the base unit
     * @param referenceQuantity how many of {@code reference} it holds: 25. With the two above, how
     *                          the screens say it: "sac 25 kg = 25 kg (87,5 kapoka)"
     */
    public record Packaging(Long id, String label, BigDecimal factor, BigDecimal price, String barcode,
                            String reference, BigDecimal referenceQuantity) {

        public static Packaging of(ProductPackaging packaging) {
            return new Packaging(packaging.getId(), packaging.getLabel(), packaging.getFactor(),
                    packaging.getPrice(), packaging.getBarcode(),
                    packaging.getReference() == null ? null : packaging.getReference().getLabel(),
                    packaging.quantityInReference());
        }
    }
}
