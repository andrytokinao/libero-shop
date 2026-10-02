package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.StatusTransitionException;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Je m'en occupe": an order taken on is its taker's alone, until handed over or given back.
 */
@SpringBootTest
class OrderHandlingTest {

    @Autowired
    private SaleService saleService;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private UserAppRepository users;
    @Autowired
    private ProductRepository products;

    private UserApp account(RoleApp... roles) {
        String handle = "h" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Compte " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.copyOf(List.of(roles))).build());
    }

    private InvoiceResponse order() {
        Product product = products.save(Product.builder().name("Produit " + UUID.randomUUID())
                .price(new BigDecimal("3000")).stockQuantity(qty(10)).build());
        return saleService.checkout(new CreateSaleRequest("Table 3", PaymentStatus.PAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(product.getId(), qty(1)))), account(RoleApp.CASHIER));
    }

    @Test
    @DisplayName("taken on, the order shows who has it and since when; nobody else may serve or take it")
    void takenOrderIsTheTakers() {
        UserApp joseph = account(RoleApp.DEPOT_AGENT);
        UserApp rado = account(RoleApp.DEPOT_AGENT);
        InvoiceResponse order = order();
        assertEquals(DeliveryStatus.PENDING, order.deliveryStatus());

        InvoiceResponse taken = invoiceService.takeOver(order.id(), joseph);
        assertEquals(DeliveryStatus.IN_PROGRESS, taken.deliveryStatus());
        assertEquals(joseph.getId(), taken.handledBy().id());
        assertNotNull(taken.handledSince());

        StatusTransitionException serve = assertThrows(StatusTransitionException.class,
                () -> invoiceService.deliver(order.id(), rado, false));
        assertEquals("HANDLED_BY_OTHER", serve.code());
        assertTrue(serve.getMessage().contains(joseph.getFullName()));

        StatusTransitionException takeAgain = assertThrows(StatusTransitionException.class,
                () -> invoiceService.takeOver(order.id(), rado));
        assertEquals("ALREADY_HANDLED", takeAgain.code());

        BusinessRuleException release = assertThrows(BusinessRuleException.class,
                () -> invoiceService.release(order.id(), rado));
        assertEquals("HANDLED_BY_OTHER", release.code());

        InvoiceResponse served = invoiceService.deliver(order.id(), joseph, false).invoice();
        assertEquals(DeliveryStatus.DELIVERED, served.deliveryStatus());
        assertEquals(joseph.getId(), served.handledBy().id(), "who served it is kept");
    }

    @Test
    @DisplayName("the depot's manager gives back an order left in someone's name; the queue takes it again")
    void managerReleases() {
        UserApp joseph = account(RoleApp.DEPOT_AGENT);
        UserApp manager = account(RoleApp.DEPOT_MANAGER);
        InvoiceResponse order = order();
        invoiceService.takeOver(order.id(), joseph);

        InvoiceResponse released = invoiceService.release(order.id(), manager);
        assertEquals(DeliveryStatus.PENDING, released.deliveryStatus());
        assertNull(released.handledBy());

        assertEquals("NOT_HANDLED", assertThrows(StatusTransitionException.class,
                () -> invoiceService.release(order.id(), joseph)).code(), "nothing left to give back");
    }

    @Test
    @DisplayName("served straight from the queue, the order still records who served it")
    void directHandOver() {
        UserApp joseph = account(RoleApp.DEPOT_AGENT);
        InvoiceResponse served = invoiceService.deliver(order().id(), joseph, false).invoice();

        assertEquals(joseph.getId(), served.handledBy().id());
    }
}
