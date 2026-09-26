package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The super-admin overview. Includes the depot cash trail — cash still in the agents'
 * hands, slips awaiting acknowledgement, slips confirmed today — which is the control
 * the role exists for.
 */
public record AdminDashboardResponse(BigDecimal revenueToday,
                                     BigDecimal stockValue,
                                     long referenceCount,
                                     long unpaidInvoices,
                                     BigDecimal unpaidAmount,
                                     long pendingDeliveries,
                                     BigDecimal depotCashInHand,
                                     long depotCashInHandCount,
                                     BigDecimal pendingRemittanceAmount,
                                     long pendingRemittanceCount,
                                     BigDecimal confirmedRemittanceAmountToday,
                                     List<RevenueBySellerResponse> revenueBySeller,
                                     List<ProductResponse> lowStockProducts) {
}
