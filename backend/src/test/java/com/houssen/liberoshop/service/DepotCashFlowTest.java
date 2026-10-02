package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RemittanceStatus;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CashRemittanceResponse;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An unpaid order's money, from the customer's hand to the till: collected by the storekeeper on
 * hand-over, brought to the desk order by order or all at once, and paid only once a cashier has
 * confirmed counting it.
 */
@SpringBootTest
class DepotCashFlowTest {

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

    private UserApp account(RoleApp... roles) {
        String handle = "u" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Compte " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.copyOf(List.of(roles))).build());
    }

    private InvoiceResponse unpaidSale(UserApp cashier, String price) {
        Product product = products.save(Product.builder().name("Produit " + UUID.randomUUID())
                .price(new BigDecimal(price)).stockQuantity(qty(10)).build());
        return saleService.checkout(new CreateSaleRequest("Client", PaymentStatus.UNPAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(product.getId(), qty(1)))), cashier);
    }

    private PaymentStatus statusOf(InvoiceResponse invoice) {
        return invoiceService.findById(invoice.id()).paymentStatus();
    }

    @Test
    @DisplayName("an unpaid order is only PAID once the desk confirms its cash, order by order")
    void oneOrderFromHandOverToTill() {
        UserApp cashier = account(RoleApp.CASHIER);
        UserApp storekeeper = account(RoleApp.DEPOT_AGENT);
        InvoiceResponse order = unpaidSale(cashier, "8000");
        assertEquals(PaymentStatus.UNPAID, statusOf(order));

        invoiceService.deliver(order.id(), storekeeper);
        assertEquals(PaymentStatus.COLLECTED, statusOf(order), "the cash is in the storekeeper's hands");
        assertEquals(0, new BigDecimal("8000").compareTo(remittanceService.cashInHandTotal(storekeeper)));

        CashRemittanceResponse slip = remittanceService.submit(storekeeper, List.of(order.id()));
        assertEquals(PaymentStatus.REMITTED, statusOf(order), "brought to the desk, not counted yet");
        assertEquals(RemittanceStatus.PENDING, slip.status());
        assertEquals(order.invoiceNumber(), slip.invoices().get(0).invoiceNumber());
        assertTrue(remittanceService.cashInHand(storekeeper).isEmpty());

        remittanceService.confirm(slip.id(), cashier);
        assertEquals(PaymentStatus.PAID, statusOf(order), "counted and confirmed: now it is paid");
    }

    @Test
    @DisplayName("the order says who holds its cash, then which slip carries it, then nothing once paid")
    void cashTrailFollowsTheMoney() {
        UserApp cashier = account(RoleApp.CASHIER);
        UserApp storekeeper = account(RoleApp.DEPOT_AGENT);
        InvoiceResponse order = unpaidSale(cashier, "4000");
        assertNull(invoiceService.findById(order.id()).cashTrail(), "unpaid: no cash anywhere yet");

        invoiceService.deliver(order.id(), storekeeper);
        InvoiceResponse.CashTrail inHand = invoiceService.findById(order.id()).cashTrail();
        assertEquals(storekeeper.getId(), inHand.holderId());
        assertEquals(storekeeper.getFullName(), inHand.holderName());
        assertNull(inHand.remittanceId());

        CashRemittanceResponse slip = remittanceService.submit(storekeeper, List.of(order.id()));
        InvoiceResponse listed = invoiceService.search(null, PaymentStatus.REMITTED, null, false, order.invoiceNumber())
                .getFirst();
        assertEquals(slip.id(), listed.cashTrail().remittanceId(), "the list carries it too");
        assertEquals(storekeeper.getId(), listed.cashTrail().holderId());

        remittanceService.confirm(slip.id(), cashier);
        assertNull(invoiceService.findById(order.id()).cashTrail(), "in the till: nothing left to follow");
    }

    @Test
    @DisplayName("an order's own button leaves the others in hand; 'everything' takes the rest")
    void perOrderThenEverything() {
        UserApp cashier = account(RoleApp.CASHIER);
        UserApp storekeeper = account(RoleApp.DEPOT_AGENT);
        InvoiceResponse first = unpaidSale(cashier, "1000");
        InvoiceResponse second = unpaidSale(cashier, "2000");
        InvoiceResponse third = unpaidSale(cashier, "3000");
        List.of(first, second, third).forEach(order -> invoiceService.deliver(order.id(), storekeeper));

        remittanceService.submit(storekeeper, List.of(first.id()));
        assertEquals(PaymentStatus.REMITTED, statusOf(first));
        assertEquals(PaymentStatus.COLLECTED, statusOf(second));

        CashRemittanceResponse rest = remittanceService.submit(storekeeper, null);
        assertEquals(2, rest.paymentCount());
        assertEquals(0, new BigDecimal("5000").compareTo(rest.amount()));
        assertEquals(PaymentStatus.REMITTED, statusOf(third));

        BusinessRuleException again = assertThrows(BusinessRuleException.class,
                () -> remittanceService.submit(storekeeper, List.of(first.id())));
        assertTrue(again.getMessage().contains("deja versee"));
    }

    @Test
    @DisplayName("a paid-at-the-desk sale is never cash in hand, even for a cashier who is also storekeeper")
    void deskMoneyIsNotDepotCash() {
        UserApp allRounder = account(RoleApp.CASHIER, RoleApp.DEPOT_AGENT);
        Product product = products.save(Product.builder().name("Produit " + UUID.randomUUID())
                .price(new BigDecimal("5000")).stockQuantity(qty(10)).build());
        saleService.checkout(new CreateSaleRequest("Client", PaymentStatus.PAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(product.getId(), qty(1)))), allRounder);

        assertTrue(remittanceService.cashInHand(allRounder).isEmpty());
    }

    @Test
    @DisplayName("a checkout cannot claim a status only the depot can reach")
    void checkoutRefusesDepotStatuses() {
        UserApp cashier = account(RoleApp.CASHIER);
        Product product = products.save(Product.builder().name("Produit " + UUID.randomUUID())
                .price(new BigDecimal("5000")).stockQuantity(qty(10)).build());

        assertThrows(BusinessRuleException.class, () -> saleService.checkout(new CreateSaleRequest("Client",
                PaymentStatus.COLLECTED, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(product.getId(), qty(1)))), cashier));
    }
}
