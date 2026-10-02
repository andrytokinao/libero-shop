package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.BusinessType;
import com.houssen.liberoshop.entity.CancelReason;
import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.ShopFeatures;
import com.houssen.liberoshop.entity.StatusTransitionException;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CashRemittanceResponse;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import com.houssen.liberoshop.web.dto.ShopSettingsResponse;
import com.houssen.liberoshop.web.dto.UpdateShopSettingsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.assertQuantity;
import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shop's configuration bends the order flow: a counter hands over at once, a restaurant
 * settles the bill at the till, a one-person depot confirms its own cash.
 */
@SpringBootTest
class ShopSettingsFlowTest {

    @Autowired
    private ShopSettingsService settingsService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private RemittanceService remittanceService;
    @Autowired
    private UserAppRepository users;
    @Autowired
    private ProductRepository products;

    /** The context is shared with the other tests, which expect the default behaviour. */
    @AfterEach
    void restoreDefault() {
        configure(ShopSettingsService.DEFAULT_TYPE, ShopSettingsService.DEFAULT_TYPE.defaults());
    }

    private ShopSettingsResponse configure(BusinessType type, ShopFeatures features) {
        return settingsService.update(new UpdateShopSettingsRequest(type, features.separateDelivery(),
                features.payAtDepot(), features.dualControlRemittance(), features.cancelAfterDelivery(),
                features.orderTakerCollects(), features.onlineOrdering()));
    }

    private UserApp account(RoleApp... roles) {
        String handle = "u" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Compte " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.copyOf(List.of(roles))).build());
    }

    private InvoiceResponse unpaidSale(UserApp seller) {
        Product product = products.save(Product.builder().name("Produit " + UUID.randomUUID())
                .price(new BigDecimal("6000")).stockQuantity(qty(10)).build());
        return saleService.checkout(new CreateSaleRequest("Table 4", PaymentStatus.UNPAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(product.getId(), qty(1)))), seller);
    }

    @Test
    @DisplayName("an installation never configured works as a wholesale depot, as before")
    void defaultIsTheFormerBehaviour() {
        ShopSettingsResponse current = settingsService.current();
        assertEquals(BusinessType.WHOLESALE_DEPOT, current.businessType());
        assertTrue(current.separateDelivery());
        assertTrue(current.payAtDepot());
        assertTrue(current.dualControlRemittance());
    }

    @Test
    @DisplayName("switches that depend on a switched-off one are switched off with it")
    void contradictoryFeaturesAreReconciled() {
        ShopSettingsResponse saved = configure(BusinessType.COUNTER, new ShopFeatures(false, true, true, false, false, false));
        assertFalse(saved.payAtDepot(), "no depot, so nothing to pay there");
        assertFalse(saved.dualControlRemittance(), "no depot cash, so nothing to confirm");
        assertEquals(saved, settingsService.current());
    }

    @Test
    @DisplayName("at a counter the customer leaves with the goods: the sale is handed over at once")
    void counterHandsOverOnSale() {
        configure(BusinessType.COUNTER, BusinessType.COUNTER.defaults());
        InvoiceResponse sale = unpaidSale(account(RoleApp.CASHIER));
        assertEquals(DeliveryStatus.DELIVERED, sale.deliveryStatus());
    }

    @Test
    @DisplayName("in a restaurant the kitchen serves without taking money, and the till settles the bill")
    void restaurantSettlesAtTheTill() {
        configure(BusinessType.RESTAURANT, BusinessType.RESTAURANT.defaults());
        UserApp cashier = account(RoleApp.CASHIER);
        UserApp kitchen = account(RoleApp.DEPOT_AGENT);
        InvoiceResponse order = unpaidSale(cashier);
        assertEquals(DeliveryStatus.PENDING, order.deliveryStatus());

        InvoiceService.DeliveryResult served = invoiceService.deliver(order.id(), kitchen);
        assertEquals(0, BigDecimal.ZERO.compareTo(served.collected()));
        assertEquals(PaymentStatus.UNPAID, served.invoice().paymentStatus());
        assertTrue(remittanceService.cashInHand(kitchen).isEmpty());

        InvoiceResponse paid = invoiceService.pay(order.id(), PaymentMethod.MOBILE_MONEY, cashier);
        assertEquals(PaymentStatus.PAID, paid.paymentStatus());

        StatusTransitionException twice = assertThrows(StatusTransitionException.class,
                () -> invoiceService.pay(order.id(), PaymentMethod.CASH, cashier));
        assertTrue(twice.getMessage().contains("deja reglee"));
    }

    @Test
    @DisplayName("the till may settle an unpaid order before the depot hands it over, whatever the type")
    void tillPaysBeforeHandOver() {
        UserApp cashier = account(RoleApp.CASHIER);
        UserApp storekeeper = account(RoleApp.DEPOT_AGENT);
        InvoiceResponse order = unpaidSale(cashier);
        invoiceService.pay(order.id(), null, cashier);

        InvoiceService.DeliveryResult delivered = invoiceService.deliver(order.id(), storekeeper);
        assertEquals(0, BigDecimal.ZERO.compareTo(delivered.collected()), "already paid: nothing to collect");
        assertEquals(PaymentStatus.PAID, delivered.invoice().paymentStatus());
    }

    @Test
    @DisplayName("an order taker records unpaid orders the till then settles, and cannot take money")
    void orderTakerNeverCollects() {
        configure(BusinessType.RESTAURANT, BusinessType.RESTAURANT.defaults());
        UserApp waiter = account(RoleApp.ORDER_TAKER);
        UserApp cashier = account(RoleApp.CASHIER);

        InvoiceResponse order = unpaidSale(waiter);
        assertEquals(PaymentStatus.UNPAID, order.paymentStatus());
        assertEquals(PaymentStatus.PAID, invoiceService.pay(order.id(), PaymentMethod.CASH, cashier).paymentStatus());

        Product product = products.save(Product.builder().name("Produit " + UUID.randomUUID())
                .price(new BigDecimal("1000")).stockQuantity(qty(10)).build());
        BusinessRuleException refused = assertThrows(BusinessRuleException.class, () -> saleService.checkout(
                new CreateSaleRequest("Table 1", PaymentStatus.PAID, PaymentMethod.CASH,
                        List.of(new CreateSaleRequest.Line(product.getId(), qty(1)))), waiter));
        assertEquals("ORDER_TAKER_CANNOT_COLLECT", refused.code());
    }

    @Test
    @DisplayName("cancelled before hand-over, an order gives its goods back and leaves every queue")
    void cancelBeforeHandOverRestocks() {
        UserApp waiter = account(RoleApp.ORDER_TAKER);
        UserApp storekeeper = account(RoleApp.DEPOT_AGENT);
        Product product = products.save(Product.builder().name("Produit " + UUID.randomUUID())
                .price(new BigDecimal("2500")).stockQuantity(qty(10)).build());
        InvoiceResponse order = saleService.checkout(new CreateSaleRequest("Table 2", PaymentStatus.UNPAID,
                PaymentMethod.CASH, List.of(new CreateSaleRequest.Line(product.getId(), qty(3)))), waiter);
        assertQuantity(7, products.findById(product.getId()).orElseThrow().getStockQuantity());

        InvoiceResponse cancelled = invoiceService.cancel(order.id(), CancelReason.INPUT_ERROR, null, waiter);
        assertEquals(PaymentStatus.CANCELLED, cancelled.paymentStatus());
        assertEquals(DeliveryStatus.CANCELLED, cancelled.deliveryStatus());
        assertEquals(CancelReason.INPUT_ERROR, cancelled.cancellation().reason());
        assertQuantity(10, products.findById(product.getId()).orElseThrow().getStockQuantity(), "back on the shelf");

        assertEquals("CANCELLED", assertThrows(StatusTransitionException.class,
                () -> invoiceService.deliver(order.id(), storekeeper)).code());
        assertEquals("ALREADY_CANCELLED", assertThrows(StatusTransitionException.class,
                () -> invoiceService.pay(order.id(), PaymentMethod.CASH, account(RoleApp.CASHIER))).code());
    }

    @Test
    @DisplayName("an order taker cancels only their own orders; 'Autre' needs a comment")
    void cancellationRules() {
        UserApp waiter = account(RoleApp.ORDER_TAKER);
        InvoiceResponse someoneElses = unpaidSale(account(RoleApp.CASHIER));

        BusinessRuleException notMine = assertThrows(BusinessRuleException.class,
                () -> invoiceService.cancel(someoneElses.id(), CancelReason.INPUT_ERROR, null, waiter));
        assertEquals("NOT_YOUR_ORDER", notMine.code());

        InvoiceResponse mine = unpaidSale(waiter);
        BusinessRuleException silent = assertThrows(BusinessRuleException.class,
                () -> invoiceService.cancel(mine.id(), CancelReason.OTHER, " ", waiter));
        assertEquals("COMMENT_REQUIRED", silent.code());
    }

    @Test
    @DisplayName("after hand-over, cancelling only records what happened, and only if the shop allows it")
    void cancelAfterHandOver() {
        configure(BusinessType.RESTAURANT, BusinessType.RESTAURANT.defaults());
        UserApp cashier = account(RoleApp.CASHIER);
        Product product = products.save(Product.builder().name("Produit " + UUID.randomUUID())
                .price(new BigDecimal("4000")).stockQuantity(qty(5)).build());
        InvoiceResponse order = saleService.checkout(new CreateSaleRequest("Table 9", PaymentStatus.UNPAID,
                PaymentMethod.CASH, List.of(new CreateSaleRequest.Line(product.getId(), qty(2)))), cashier);
        invoiceService.deliver(order.id(), account(RoleApp.DEPOT_AGENT));

        StatusTransitionException refused = assertThrows(StatusTransitionException.class,
                () -> invoiceService.cancel(order.id(), CancelReason.CUSTOMER_GAVE_UP, "parti", cashier));
        assertEquals("ALREADY_DELIVERED", refused.code());

        configure(BusinessType.RESTAURANT, new ShopFeatures(true, false, false, true, false, false));
        assertEquals("COMMENT_REQUIRED", assertThrows(BusinessRuleException.class,
                () -> invoiceService.cancel(order.id(), CancelReason.CUSTOMER_GAVE_UP, null, cashier)).code());

        InvoiceResponse cancelled = invoiceService.cancel(order.id(), CancelReason.CUSTOMER_GAVE_UP,
                "Parti sans payer", cashier);
        assertEquals(PaymentStatus.CANCELLED, cancelled.paymentStatus());
        assertEquals(DeliveryStatus.DELIVERED, cancelled.deliveryStatus(), "it was handed over all the same");
        assertEquals("Parti sans payer", cancelled.cancellation().comment());
        assertQuantity(3, products.findById(product.getId()).orElseThrow().getStockQuantity(), "the goods are gone");
    }

    @Test
    @DisplayName("a receptionist allowed to take money holds it until the till confirms it")
    void receptionistBringsCashToTheTill() {
        configure(BusinessType.HOTEL, BusinessType.HOTEL.defaults());
        UserApp receptionist = account(RoleApp.ORDER_TAKER);
        UserApp cashier = account(RoleApp.CASHIER);
        Product product = products.save(Product.builder().name("Produit " + UUID.randomUUID())
                .price(new BigDecimal("3000")).stockQuantity(qty(10)).build());

        // Paid on the spot: cash in hand, not yet in the till.
        InvoiceResponse paidNow = saleService.checkout(new CreateSaleRequest("Chambre 12", PaymentStatus.PAID,
                PaymentMethod.MOBILE_MONEY, List.of(new CreateSaleRequest.Line(product.getId(), qty(1)))), receptionist);
        assertEquals(PaymentStatus.COLLECTED, paidNow.paymentStatus());

        // Paid later, from "Mes commandes".
        InvoiceResponse paidLater = unpaidSale(receptionist);
        assertEquals(PaymentStatus.COLLECTED, invoiceService.collect(paidLater.id(), receptionist).paymentStatus());
        assertEquals(0, new BigDecimal("9000").compareTo(remittanceService.cashInHandTotal(receptionist)));

        CashRemittanceResponse slip = remittanceService.submit(receptionist, null);
        assertEquals(2, slip.paymentCount());
        remittanceService.confirm(slip.id(), cashier);
        assertEquals(PaymentStatus.PAID, invoiceService.findById(paidNow.id()).paymentStatus());
        assertEquals(PaymentStatus.PAID, invoiceService.findById(paidLater.id()).paymentStatus());
    }

    @Test
    @DisplayName("without the switch, an order taker cannot take money later either")
    void collectNeedsTheSwitch() {
        UserApp waiter = account(RoleApp.ORDER_TAKER);
        InvoiceResponse order = unpaidSale(waiter);
        assertEquals("ORDER_TAKER_CANNOT_COLLECT", assertThrows(BusinessRuleException.class,
                () -> invoiceService.collect(order.id(), waiter)).code());
    }

    @Test
    @DisplayName("without dual control, whoever brought the depot cash may confirm it")
    void oneRunnerConfirmsOwnCash() {
        UserApp allRounder = account(RoleApp.CASHIER, RoleApp.DEPOT_AGENT);

        InvoiceResponse first = unpaidSale(allRounder);
        invoiceService.deliver(first.id(), allRounder);
        CashRemittanceResponse refused = remittanceService.submit(allRounder, List.of(first.id()));
        assertThrows(BusinessRuleException.class, () -> remittanceService.confirm(refused.id(), allRounder));

        configure(BusinessType.GROCERY_WITH_DEPOT, BusinessType.GROCERY_WITH_DEPOT.defaults());
        remittanceService.confirm(refused.id(), allRounder);
        assertEquals(PaymentStatus.PAID, invoiceService.findById(first.id()).paymentStatus());
    }
}
