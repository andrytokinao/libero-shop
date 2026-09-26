package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.SaleLine;

import java.math.BigDecimal;

public record SaleLineResponse(Long id,
                               int quantity,
                               BigDecimal unitPrice,
                               BigDecimal subtotal,
                               ProductResponse product) {

    public static SaleLineResponse of(SaleLine line) {
        return new SaleLineResponse(
                line.getId(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())),
                ProductResponse.of(line.getProduct()));
    }
}
