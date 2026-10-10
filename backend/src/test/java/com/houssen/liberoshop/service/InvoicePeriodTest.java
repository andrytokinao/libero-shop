package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The orders read a period at a time: a screen that names none gets the last
 * {@link InvoiceService#HISTORY_DAYS} days and what is still open, never the whole history.
 */
@SpringBootTest
class InvoicePeriodTest {

    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private BusinessCalendar calendar;
    @Autowired
    private ProductRepository products;
    @Autowired
    private UserAppRepository users;
    @Autowired
    private JdbcTemplate jdbc;

    /** Found in no other test's data: what the searches below are narrowed to. */
    private String marker;
    private UserApp admin;
    private LocalDate longAgo;

    private Long recent;
    private Long oldAndDone;
    private Long oldAndUnpaid;
    private Long oldAndWaiting;

    @BeforeEach
    void ordersOfTodayAndOfLongAgo() {
        marker = "zp" + UUID.randomUUID().toString().substring(0, 8);
        String handle = "a" + UUID.randomUUID().toString().substring(0, 8);
        admin = users.save(UserApp.builder().fullName("Gerant " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.SUPER_ADMIN, RoleApp.CASHIER)).build());
        Product rice = products.save(Product.builder().name("Riz " + marker)
                .price(new BigDecimal("2000")).stockQuantity(qty(100)).build());
        longAgo = calendar.today().minusDays(InvoiceService.HISTORY_DAYS + 10L);

        recent = sell(rice, PaymentStatus.PAID);
        oldAndDone = sell(rice, PaymentStatus.PAID);
        oldAndUnpaid = sell(rice, PaymentStatus.UNPAID);
        oldAndWaiting = sell(rice, PaymentStatus.PAID);
        // No transition leads back in time: the rows are aged, and two of them closed, by hand.
        age(oldAndDone, "DELIVERED");
        age(oldAndUnpaid, "DELIVERED");
        age(oldAndWaiting, "PENDING");
    }

    private Long sell(Product product, PaymentStatus status) {
        return saleService.checkout(new CreateSaleRequest("Client " + marker, status,
                status == PaymentStatus.PAID ? PaymentMethod.CASH : null,
                List.of(new CreateSaleRequest.Line(product.getId(), qty(1)))), admin).id();
    }

    private void age(Long invoiceId, String deliveryStatus) {
        jdbc.update("update invoice set invoice_date = ?, delivery_status = ? where id = ?",
                LocalDateTime.of(longAgo, java.time.LocalTime.NOON), deliveryStatus, invoiceId);
    }

    private Set<Long> found(LocalDate from, LocalDate to) {
        return invoiceService.searchFor(admin, false, null, null, false, from, to, marker).stream()
                .map(InvoiceResponse::id)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Sans periode : les 30 derniers jours, et ce qui est encore ouvert quel que soit son age")
    void withoutPeriod() {
        assertEquals(Set.of(recent, oldAndUnpaid, oldAndWaiting), found(null, null));
    }

    @Test
    @DisplayName("Avec une periode : ses jours et rien d'autre, le dernier jour compris")
    void withPeriod() {
        Set<Long> old = Set.of(oldAndDone, oldAndUnpaid, oldAndWaiting);

        assertEquals(old, found(longAgo, longAgo));
        assertEquals(old, found(null, longAgo));
        assertEquals(Set.of(), found(longAgo.plusDays(1), calendar.today().minusDays(1)));
        assertEquals(Set.of(recent), found(calendar.today(), null));
        assertEquals(4, found(longAgo, calendar.today()).size());
    }

    @Test
    @DisplayName("Aujourd'hui seulement l'emporte sur la periode par defaut")
    void todayOnly() {
        Set<Long> today = invoiceService.searchFor(admin, false, null, null, true, null, null, marker).stream()
                .map(InvoiceResponse::id)
                .collect(Collectors.toSet());

        assertEquals(Set.of(recent), today);
    }

    @Test
    @DisplayName("Le filtre de statut s'ajoute a la periode par defaut")
    void statusWithinDefaultPeriod() {
        List<InvoiceResponse> unpaid = invoiceService.searchFor(admin, false, PaymentStatus.UNPAID, null,
                false, null, null, marker);

        assertEquals(List.of(oldAndUnpaid), unpaid.stream().map(InvoiceResponse::id).toList());
    }

    @Test
    @DisplayName("Une fin avant le debut est refusee")
    void endBeforeStart() {
        assertThrows(BusinessRuleException.class,
                () -> invoiceService.searchFor(admin, false, null, null, false,
                        calendar.today(), calendar.today().minusDays(1), null));
    }
}
