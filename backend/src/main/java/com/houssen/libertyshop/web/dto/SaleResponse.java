package com.houssen.libertyshop.web.dto;

import com.houssen.libertyshop.entity.PaymentStatus;
import com.houssen.libertyshop.entity.Sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SaleResponse(Long id,
                           LocalDateTime saleDate,
                           PaymentStatus paymentStatus,
                           BigDecimal totalAmount,
                           UserResponse seller,
                           List<SaleLineResponse> lines) {

    public static SaleResponse of(Sale sale) {
        return new SaleResponse(
                sale.getId(),
                sale.getSaleDate(),
                sale.getPaymentStatus(),
                sale.getTotalAmount(),
                UserResponse.of(sale.getSeller()),
                sale.getLines().stream().map(SaleLineResponse::of).toList());
    }
}
