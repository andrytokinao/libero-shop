package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.BusinessType;
import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RemittanceStatus;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.ShopFeatures;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CashRemittanceResponse;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
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

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cash brought to the till by someone who holds the till: confirmed as it is filed, unless the
 * shop wants a second person to count it.
 */
@SpringBootTest
class RemittanceSelfConfirmTest {

    @Autowired
    private SaleService saleService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private RemittanceService remittanceService;
    @Autowired
    private ShopSettingsService settingsService;
    @Autowired
    private UserAppRepository users;
    @Autowired
    private ProductRepository products;

    /** The context is shared with the other tests, which expect the default behaviour. */
    @AfterEach
    void restoreDefault() {
        dualControl(ShopSettingsService.DEFAULT_TYPE.defaults().dualControlRemittance());
    }

    private void dualControl(boolean on) {
        ShopFeatures depot = BusinessType.WHOLESALE_DEPOT.defaults();
        settingsService.update(new UpdateShopSettingsRequest(BusinessType.WHOLESALE_DEPOT, depot.separateDelivery(),
                depot.payAtDepot(), on, depot.cancelAfterDelivery(), depot.orderTakerCollects(),
                depot.onlineOrdering()));
    }

    private UserApp account(RoleApp... roles) {
        String handle = "r" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Compte " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.copyOf(List.of(roles))).build());
    }

    /** An unpaid order handed over by {@code agent}, who takes its cash. */
    private InvoiceResponse collectedBy(UserApp agent) {
        Product product = products.save(Product.builder().name("Produit " + UUID.randomUUID())
                .price(new BigDecimal("6000")).stockQuantity(qty(10)).build());
        InvoiceResponse order = saleService.checkout(new CreateSaleRequest("Client", PaymentStatus.UNPAID,
                PaymentMethod.CASH, List.of(new CreateSaleRequest.Line(product.getId(), qty(1)))),
                account(RoleApp.CASHIER));
        invoiceService.deliver(order.id(), agent);
        return order;
    }

    @Test
    @DisplayName("a cashier who is also storekeeper puts the cash straight in the till")
    void cashierConfirmsOwnSlip() {
        dualControl(false);
        UserApp fatima = account(RoleApp.CASHIER, RoleApp.DEPOT_AGENT);
        InvoiceResponse order = collectedBy(fatima);

        CashRemittanceResponse slip = remittanceService.submit(fatima, List.of(order.id()));

        assertEquals(RemittanceStatus.CONFIRMED, slip.status());
        assertEquals(fatima.getId(), slip.confirmedBy().id());
        assertEquals(PaymentStatus.PAID, invoiceService.findById(order.id()).paymentStatus());
    }

    @Test
    @DisplayName("with dual control on, even a cashier's own slip waits for a colleague")
    void dualControlKeepsTheSecondPerson() {
        dualControl(true);
        UserApp fatima = account(RoleApp.CASHIER, RoleApp.DEPOT_AGENT);
        InvoiceResponse order = collectedBy(fatima);

        CashRemittanceResponse slip = remittanceService.submit(fatima, List.of(order.id()));

        assertEquals(RemittanceStatus.PENDING, slip.status());
        assertEquals("SELF_CONFIRMATION", assertThrows(BusinessRuleException.class,
                () -> remittanceService.confirm(slip.id(), fatima)).code());
        remittanceService.confirm(slip.id(), account(RoleApp.CASHIER));
        assertEquals(PaymentStatus.PAID, invoiceService.findById(order.id()).paymentStatus());
    }

    @Test
    @DisplayName("a storekeeper who holds no till still waits for the cashier, whatever the setting")
    void storekeeperWaits() {
        dualControl(false);
        UserApp joseph = account(RoleApp.DEPOT_AGENT);
        InvoiceResponse order = collectedBy(joseph);

        CashRemittanceResponse slip = remittanceService.submit(joseph, List.of(order.id()));

        assertEquals(RemittanceStatus.PENDING, slip.status());
        assertEquals(PaymentStatus.REMITTED, invoiceService.findById(order.id()).paymentStatus());
    }
}
