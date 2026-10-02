package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.SaleLine;

import java.math.BigDecimal;

public record SaleLineResponse(Long id,
                               BigDecimal quantity,
                               String unitLabel,
                               BigDecimal unitFactor,
                               BigDecimal unitPrice,
                               BigDecimal subtotal,
                               ProductResponse product) {

    public static SaleLineResponse of(SaleLine line) {
        return new SaleLineResponse(
                line.getId(),
                line.getQuantity(),
                line.getUnitLabel(),
                line.factor(),
                line.getUnitPrice(),
                line.lineTotal(),
                ProductResponse.withoutUnits(line.getProduct()));
    }
}
