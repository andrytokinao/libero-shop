package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.Sale;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SaleRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import com.houssen.liberoshop.web.dto.PaymentMethodBreakdownResponse;
import com.houssen.liberoshop.web.dto.RevenueReportResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The day's report counts money by the day it was paid, in its total as in its split by method. */
@SpringBootTest
class DailyRevenueTest {

    @Autowired
    private DashboardService dashboard;
    @Autowired
    private SaleService saleService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private SaleRepository sales;
    @Autowired
    private ProductRepository products;
    @Autowired
    private UserAppRepository users;
    @Autowired
    private BusinessCalendar calendar;

    private static BigDecimal byMethod(RevenueReportResponse report) {
        return report.byPaymentMethod().stream()
                .map(PaymentMethodBreakdownResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("un credit vendu hier et paye aujourd'hui compte aujourd'hui, au total comme par moyen de paiement")
    void creditDHierPayeAujourdhui() {
        String handle = "c" + UUID.randomUUID().toString().substring(0, 8);
        UserApp cashier = users.save(UserApp.builder().fullName("Caisse " + handle).username(handle)
                .password("x").enabled(true).roles(EnumSet.of(RoleApp.CASHIER)).build());
        Product riz = products.save(Product.builder().name("Riz " + handle).price(new BigDecimal("12500"))
                .stockQuantity(qty(10)).build());
        InvoiceResponse credit = saleService.checkout(new CreateSaleRequest("Rina", PaymentStatus.UNPAID,
                PaymentMethod.CASH, List.of(new CreateSaleRequest.Line(riz.getId(), qty(2)))), cashier);
        Sale sale = sales.findById(credit.sale().id()).orElseThrow();
        sale.setSaleDate(calendar.startOfToday().minusHours(5));
        sales.save(sale);
        RevenueReportResponse before = dashboard.revenueReport();

        invoiceService.pay(credit.id(), PaymentMethod.CASH, cashier);
        RevenueReportResponse after = dashboard.revenueReport();

        BigDecimal amount = new BigDecimal("25000");
        assertEquals(0, amount.compareTo(after.totalToday().subtract(before.totalToday())), "in the day's total");
        assertEquals(0, amount.compareTo(byMethod(after).subtract(byMethod(before))), "in the split by method");
        assertEquals(0, after.totalToday().compareTo(byMethod(after)), "the total and its split agree");
    }
}
