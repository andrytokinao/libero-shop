package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record StockDashboardResponse(BigDecimal stockValue,
                                     long referenceCount,
                                     BigDecimal unitsInStock,
                                     long lowStockCount,
                                     long suppliesLastWeek,
                                     BigDecimal unitsSuppliedLastWeek,
                                     long deliveredInvoices,
                                     List<ProductResponse> toRestock) {
}
