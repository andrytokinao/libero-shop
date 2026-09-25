package com.houssen.libertyshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record DepotDashboardResponse(long pendingDeliveries,
                                     long unpaidPendingDeliveries,
                                     BigDecimal cashInHand,
                                     long cashInHandCount,
                                     long deliveredTotal,
                                     long deliveredToday,
                                     BigDecimal deliveredValueToday,
                                     long lowStockCount,
                                     List<InvoiceResponse> pendingInvoices) {
}
