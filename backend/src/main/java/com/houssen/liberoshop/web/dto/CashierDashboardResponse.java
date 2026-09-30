package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the cash-desk dashboard shows, in one round trip.
 *
 * @param unpaidInvoicesToday   today's invoices whose money is not in the till yet, wherever it is
 * @param remittancesToConfirm  slips brought by the depot and not confirmed yet, every agent's --
 *                              any cashier may confirm them
 */
public record CashierDashboardResponse(BigDecimal revenueToday,
                                       long paidSalesToday,
                                       long invoicesToday,
                                       long unpaidInvoicesToday,
                                       BigDecimal averageBasket,
                                       BigDecimal outstandingToday,
                                       long lowStockCount,
                                       long remittancesToConfirm,
                                       BigDecimal remittancesToConfirmAmount,
                                       List<InvoiceResponse> latestInvoices) {
}
