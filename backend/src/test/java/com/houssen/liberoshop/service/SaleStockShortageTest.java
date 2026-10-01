package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.InsufficientStockException;
import com.houssen.liberoshop.service.exception.InsufficientStockException.Shortage;
import com.houssen.liberoshop.web.ApiExceptionHandler;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sale the shelf cannot fill: every short line named at once, with the ids the till needs to
 * mark them in red, and nothing written.
 */
@SpringBootTest
class SaleStockShortageTest {

    @Autowired
    private SaleService saleService;
    @Autowired
    private ProductRepository products;
    @Autowired
    private UserAppRepository users;

    private Product product(String name, int stock) {
        return products.save(Product.builder().name(name + " " + UUID.randomUUID().toString().substring(0, 6))
                .price(new BigDecimal("1000")).stockQuantity(stock).build());
    }

    private UserApp cashier() {
        String handle = "c" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Caisse " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.CASHIER)).build());
    }

    @Test
    @DisplayName("every short line is reported, in the order entered, and no stock moves")
    void reportsEveryShortLine() {
        Product sucre = product("Sucre", 6);
        Product riz = product("Riz", 50);
        Product eau = product("Eau", 4);

        InsufficientStockException refused = assertThrows(InsufficientStockException.class, () ->
                saleService.checkout(new CreateSaleRequest("Client", PaymentStatus.PAID, PaymentMethod.CASH,
                        List.of(new CreateSaleRequest.Line(eau.getId(), 5),
                                new CreateSaleRequest.Line(riz.getId(), 2),
                                new CreateSaleRequest.Line(sucre.getId(), 9))), cashier()));

        assertEquals(List.of(
                        new Shortage(eau.getId(), eau.getName(), 5, 4),
                        new Shortage(sucre.getId(), sucre.getName(), 9, 6)),
                refused.shortages());
        assertTrue(refused.getMessage().startsWith("Stock insuffisant pour " + eau.getName()));
        assertEquals(50, products.findById(riz.getId()).orElseThrow().getStockQuantity(),
                "the line that could be served is not served alone");
        assertEquals(4, products.findById(eau.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    @DisplayName("the shortages travel in the error's data; other refusals carry none")
    void shortagesAreSerialized() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        JsonMapper json = JsonMapper.builder().findAndAddModules().build();

        String shortage = json.writeValueAsString(handler.handleBusinessRule(
                new InsufficientStockException(List.of(new Shortage(7L, "Eau", 5, 4)))).getBody());
        assertTrue(shortage.contains("\"code\":\"INSUFFICIENT_STOCK\""));
        assertTrue(shortage.contains("\"data\":[{\"productId\":7,\"productName\":\"Eau\",\"requested\":5,\"available\":4}]"),
                shortage);

        String other = json.writeValueAsString(handler.handleBusinessRule(
                new com.houssen.liberoshop.service.exception.BusinessRuleException("EMPTY_CART", "Le panier est vide."))
                .getBody());
        assertFalse(other.contains("\"data\""), other);
    }
}
