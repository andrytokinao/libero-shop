package com.houssen.liberoshop.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
@Builder
public record RevenueBySellerResponse(Long sellerId, String sellerName, BigDecimal amount, BigDecimal benefit, long saleCount) {
}
