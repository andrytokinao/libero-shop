package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;

public record RevenueBySellerResponse(Long sellerId, String sellerName, BigDecimal amount, long saleCount) {
}
