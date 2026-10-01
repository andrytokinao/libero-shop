package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.BusinessType;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.ShopFeatures;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.DiningTableResponse;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import com.houssen.liberoshop.web.dto.PublicOrderRequest;
import com.houssen.liberoshop.web.dto.PublicOrderResponse;
import com.houssen.liberoshop.web.dto.UpdateShopSettingsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A customer orders from their table's QR code; the waiter serves and is paid, or leaves it to the till. */
@SpringBootTest
class OnlineOrderServiceTest {

    @Autowired
    private OnlineOrderService onlineOrders;
    @Autowired
    private ShopSettingsService settingsService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private ProductRepository products;
    @Autowired
    private UserAppRepository users;

    private Product dish;

    @BeforeEach
    void restaurantWithOnlineOrdering() {
        configure(new ShopFeatures(true, true, false, false, false, true));
        dish = products.save(Product.builder().name("Plat " + UUID.randomUUID())
                .price(new BigDecimal("12000")).stockQuantity(50).build());
    }

    @AfterEach
    void restoreDefault() {
        ShopFeatures defaults = ShopSettingsService.DEFAULT_TYPE.defaults();
        settingsService.update(new UpdateShopSettingsRequest(ShopSettingsService.DEFAULT_TYPE,
                defaults.separateDelivery(), defaults.payAtDepot(), defaults.dualControlRemittance(),
                defaults.cancelAfterDelivery(), defaults.orderTakerCollects(), defaults.onlineOrdering()));
    }

    private void configure(ShopFeatures f) {
        settingsService.update(new UpdateShopSettingsRequest(BusinessType.RESTAURANT, f.separateDelivery(),
                f.payAtDepot(), f.dualControlRemittance(), f.cancelAfterDelivery(), f.orderTakerCollects(),
                f.onlineOrdering()));
    }

    private DiningTableResponse table() {
        return onlineOrders.createTable("Table " + UUID.randomUUID().toString().substring(0, 6));
    }

    private PublicOrderResponse order(DiningTableResponse table, String name, int quantity) {
        return onlineOrders.order(table.token(), new PublicOrderRequest(name,
                List.of(new CreateSaleRequest.Line(dish.getId(), quantity))));
    }

    private UserApp account(RoleApp role) {
        String handle = "u" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Compte " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(role)).build());
    }

    private InvoiceResponse invoiceOf(PublicOrderResponse order) {
        return invoiceService.search(null, null, null, false, order.invoiceNumber()).getFirst();
    }

    @Test
    @DisplayName("an order from a table is an unpaid sale named after the table, by an account nobody signs in to")
    void orderFromTheTable() {
        DiningTableResponse table = table();
        assertTrue(onlineOrders.menu(table.token()).items().stream().anyMatch(item -> item.id().equals(dish.getId())));

        PublicOrderResponse order = order(table, " Rina ", 2);
        assertEquals("RECEIVED", order.status());
        assertFalse(order.paid());
        assertEquals(0, new BigDecimal("24000").compareTo(order.total()));

        InvoiceResponse invoice = invoiceOf(order);
        assertEquals(table.name() + " · Rina", invoice.clientName());
        assertEquals(PaymentStatus.UNPAID, invoice.paymentStatus());
        assertEquals(OnlineOrderService.ONLINE_ACCOUNT, invoice.sale().seller().username());
        assertFalse(users.findByUsername(OnlineOrderService.ONLINE_ACCOUNT).orElseThrow().isEnabled());
    }

    @Test
    @DisplayName("the waiter serves without taking the money: the order is served and left to the till")
    void servedThenPaidAtTheTill() {
        DiningTableResponse table = table();
        PublicOrderResponse order = order(table, null, 1);
        InvoiceResponse invoice = invoiceOf(order);

        InvoiceService.DeliveryResult served = invoiceService.deliver(invoice.id(), account(RoleApp.DEPOT_AGENT), false);
        assertEquals(0, BigDecimal.ZERO.compareTo(served.collected()));
        assertEquals("SERVED", onlineOrders.status(table.token(), order.invoiceNumber()).status());

        invoiceService.pay(invoice.id(), null, account(RoleApp.CASHIER));
        assertTrue(onlineOrders.status(table.token(), order.invoiceNumber()).paid());
    }

    @Test
    @DisplayName("the waiter may also take the money on serving, as the depot always did")
    void servedAndPaidToTheWaiter() {
        DiningTableResponse table = table();
        InvoiceResponse invoice = invoiceOf(order(table, null, 1));
        InvoiceService.DeliveryResult served = invoiceService.deliver(invoice.id(), account(RoleApp.DEPOT_AGENT), true);
        assertEquals(PaymentStatus.COLLECTED, served.invoice().paymentStatus());
    }

    @Test
    @DisplayName("a table orders once every few seconds, a few units an article at most")
    void abuseLimits() {
        DiningTableResponse table = table();
        assertEquals("TOO_MANY_UNITS", assertThrows(BusinessRuleException.class,
                () -> order(table, null, OnlineOrderService.MAX_UNITS_PER_LINE + 1)).code());

        order(table, null, 1);
        assertEquals("ORDER_TOO_SOON", assertThrows(BusinessRuleException.class,
                () -> order(table, null, 1)).code());
    }

    @Test
    @DisplayName("a withdrawn code, another table's order, or the switch off: nothing is served")
    void closedDoors() {
        DiningTableResponse table = table();
        PublicOrderResponse order = order(table, null, 1);
        DiningTableResponse other = table();

        assertThrows(ResourceNotFoundException.class, () -> onlineOrders.status(other.token(), order.invoiceNumber()));

        DiningTableResponse renewed = onlineOrders.regenerateToken(table.id());
        assertThrows(ResourceNotFoundException.class, () -> onlineOrders.menu(table.token()));
        assertEquals("RECEIVED", onlineOrders.status(renewed.token(), order.invoiceNumber()).status());

        configure(new ShopFeatures(true, true, false, false, false, false));
        assertThrows(ResourceNotFoundException.class, () -> onlineOrders.menu(renewed.token()));
    }
}
