package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record RevenueReportResponse(BigDecimal totalToday,
                                    BigDecimal averageBasket,
                                    RevenueBySellerResponse topSeller,
                                    List<RevenueBySellerResponse> bySeller,
                                    List<PaymentMethodBreakdownResponse> byPaymentMethod) {
}
