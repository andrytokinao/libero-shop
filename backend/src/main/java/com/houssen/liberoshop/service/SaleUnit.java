package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.service.exception.BusinessRuleException;

import java.math.BigDecimal;

/**
 * The unit one line of a sale is sold in, as the checkout resolves it: its name, how many base
 * units it holds, and its price.
 *
 * <p>The base unit and a packaging answer the same three questions, so the checkout asks this
 * and never has to know which of the two it is holding.
 *
 * @param label  null for the base unit of a product counted in bare units
 * @param factor 1 for the base unit
 */
public record SaleUnit(String label, BigDecimal factor, BigDecimal price) {

    /**
     * The unit a posted line names: one of the product's packagings, or its base unit when none.
     *
     * @param packagingId null for the base unit
     * @throws BusinessRuleException when the product has no such unit -- a screen still showing a
     *                               unit deleted since, or a payload naming another product's
     */
    public static SaleUnit of(Product product, Long packagingId) {
        if (packagingId == null) {
            return new SaleUnit(product.getUnit(), BigDecimal.ONE, product.getPrice());
        }
        ProductPackaging packaging = product.getPackagings().stream()
                .filter(candidate -> candidate.getId().equals(packagingId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_SALE_UNIT",
                        "\"" + product.getName() + "\" ne se vend plus dans cette unite. "
                                + "Rechargez l'ecran et choisissez-en une autre."));
        return new SaleUnit(packaging.getLabel(), packaging.getFactor(), packaging.getPrice());
    }
}
