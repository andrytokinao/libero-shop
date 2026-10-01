package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Sale;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.PaymentRepository;
import com.houssen.liberoshop.repository.SaleRepository;
import com.houssen.liberoshop.web.dto.AdminDashboardResponse;
import com.houssen.liberoshop.web.dto.CashierDashboardResponse;
import com.houssen.liberoshop.web.dto.DepotDashboardResponse;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import com.houssen.liberoshop.web.dto.PaymentMethodBreakdownResponse;
import com.houssen.liberoshop.web.dto.RevenueBySellerResponse;
import com.houssen.liberoshop.web.dto.RevenueReportResponse;
import com.houssen.liberoshop.web.dto.StockDashboardResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Assembles the figures behind each role's landing page.
 *
 * <p>One endpoint per dashboard rather than a dozen counters the client would have to
 * fetch and add up: the numbers shown side by side are then read from one consistent
 * snapshot, and the arithmetic that defines them lives next to the data.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int LATEST_INVOICES = 6;

    private final SaleRepository sales;
    private final PaymentRepository payments;
    private final InvoiceService invoiceService;
    private final CatalogService catalogService;
    private final StockService stockService;
    private final RemittanceService remittanceService;
    private final BusinessCalendar calendar;

    public DashboardService(SaleRepository sales, PaymentRepository payments,
                            InvoiceService invoiceService, CatalogService catalogService,
                            StockService stockService, RemittanceService remittanceService,
                            BusinessCalendar calendar) {
        this.sales = sales;
        this.payments = payments;
        this.invoiceService = invoiceService;
        this.catalogService = catalogService;
        this.stockService = stockService;
        this.remittanceService = remittanceService;
        this.calendar = calendar;
    }

    public CashierDashboardResponse forCashier(UserApp cashier) {
        // A cancelled order is neither a sale of the day nor money owed.
        List<InvoiceResponse> mine = invoiceService.search(cashier.getId(), null, null, true, null).stream()
                .filter(invoice -> invoice.paymentStatus() != PaymentStatus.CANCELLED)
                .toList();
        List<InvoiceResponse> paid = mine.stream()
                .filter(invoice -> invoice.paymentStatus() == PaymentStatus.PAID)
                .toList();
        // Not in the till yet: unpaid, or paid to a storekeeper whose cash has not been confirmed.
        List<InvoiceResponse> unpaid = mine.stream()
                .filter(invoice -> invoice.paymentStatus() != PaymentStatus.PAID)
                .toList();

        BigDecimal revenue = totalOf(paid);
        return new CashierDashboardResponse(
                revenue,
                paid.size(),
                mine.size(),
                unpaid.size(),
                average(revenue, paid.size()),
                totalOf(unpaid),
                catalogService.countLowStock(),
                remittanceService.pendingCount(),
                remittanceService.pendingTotal(),
                mine.stream().limit(LATEST_INVOICES).toList());
    }

    public DepotDashboardResponse forDepotAgent(UserApp agent) {
        List<InvoiceResponse> pending =
                invoiceService.search(null, null, DeliveryStatus.PENDING, false, null);
        List<InvoiceResponse> deliveredToday =
                invoiceService.search(null, null, DeliveryStatus.DELIVERED, true, null);

        return new DepotDashboardResponse(
                pending.size(),
                pending.stream().filter(i -> i.paymentStatus() == PaymentStatus.UNPAID).count(),
                remittanceService.cashInHandTotal(agent),
                remittanceService.cashInHand(agent).size(),
                invoiceService.countDelivered(),
                deliveredToday.size(),
                totalOf(deliveredToday),
                catalogService.countLowStock(),
                pending);
    }

    public StockDashboardResponse forDepotManager() {
        return new StockDashboardResponse(
                catalogService.totalStockValue(),
                catalogService.countProducts(),
                catalogService.totalUnitsInStock(),
                catalogService.countLowStock(),
                stockService.countSuppliesLastWeek(),
                stockService.unitsSuppliedLastWeek(),
                invoiceService.countDelivered(),
                catalogService.findLowStock());
    }

    public AdminDashboardResponse forAdmin() {
        List<RevenueBySellerResponse> bySeller = revenueBySellerToday();
        return new AdminDashboardResponse(
                bySeller.stream().map(RevenueBySellerResponse::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                catalogService.totalStockValue(),
                catalogService.countProducts(),
                invoiceService.countUnpaid(),
                invoiceService.unpaidAmount(),
                invoiceService.countPendingDeliveries(),
                remittanceService.depotCashInHandTotal(),
                remittanceService.depotCashInHandCount(),
                remittanceService.pendingTotal(),
                remittanceService.pendingCount(),
                remittanceService.confirmedTodayTotal(),
                bySeller,
                catalogService.findLowStock());
    }

    public RevenueReportResponse revenueReport() {
        List<RevenueBySellerResponse> bySeller = revenueBySellerToday();
        BigDecimal total = bySeller.stream()
                .map(RevenueBySellerResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long saleCount = bySeller.stream().mapToLong(RevenueBySellerResponse::saleCount).sum();

        List<PaymentMethodBreakdownResponse> byMethod =
                payments.aggregateByMethod(calendar.startOfToday(), calendar.startOfTomorrow()).stream()
                        .map(row -> new PaymentMethodBreakdownResponse(
                                (PaymentMethod) row[0],
                                ((Number) row[1]).longValue(),
                                (BigDecimal) row[2]))
                        .toList();

        return new RevenueReportResponse(
                total,
                average(total, saleCount),
                bySeller.isEmpty() ? null : bySeller.get(0),
                bySeller,
                byMethod);
    }

    /** Cashed-in revenue per seller for the current business day, best seller first. */
    public List<RevenueBySellerResponse> revenueBySellerToday() {
        return sales.aggregateRevenueBySeller(PaymentStatus.PAID,
                        calendar.startOfToday(), calendar.startOfTomorrow()).stream()
                .map(row -> new RevenueBySellerResponse(
                        (Long) row[0],
                        (String) row[1],
                        (BigDecimal) row[2],
                        ((Number) row[3]).longValue()))
                .toList();
    }

    /** Sales recorded today, whatever their payment status. Used by the user activity list. */
    public List<Sale> salesToday() {
        LocalDateTime from = calendar.startOfToday();
        LocalDateTime to = calendar.startOfTomorrow();
        return sales.findBySaleDateBetweenOrderBySaleDateDesc(from, to);
    }

    private static BigDecimal totalOf(List<InvoiceResponse> list) {
        return list.stream()
                .map(invoice -> invoice.sale().totalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal average(BigDecimal total, long count) {
        return count == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP);
    }
}
