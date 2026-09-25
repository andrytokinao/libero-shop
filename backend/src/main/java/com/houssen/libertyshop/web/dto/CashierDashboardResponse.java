package com.houssen.libertyshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

/** Everything the cash-desk dashboard shows, in one round trip. */
public record CashierDashboardResponse(BigDecimal revenueToday,
                                       long paidSalesToday,
                                       long invoicesToday,
                                       long unpaidInvoicesToday,
                                       BigDecimal averageBasket,
                                       BigDecimal outstandingToday,
                                       long lowStockCount,
                                       List<InvoiceResponse> latestInvoices) {
}
