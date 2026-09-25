package com.houssen.libertyshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record StockDashboardResponse(BigDecimal stockValue,
                                     long referenceCount,
                                     long unitsInStock,
                                     long lowStockCount,
                                     long suppliesLastWeek,
                                     long unitsSuppliedLastWeek,
                                     long deliveredInvoices,
                                     List<ProductResponse> toRestock) {
}
