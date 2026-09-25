package com.houssen.libertyshop.web.dto;

import java.math.BigDecimal;

public record RevenueBySellerResponse(Long sellerId, String sellerName, BigDecimal amount, long saleCount) {
}
