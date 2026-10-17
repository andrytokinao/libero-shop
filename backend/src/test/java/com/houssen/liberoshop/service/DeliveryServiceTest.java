package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CreateDeliveryRequest;
import com.houssen.liberoshop.web.dto.SupplyResponse;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A delivery note booked at once: every line, or none. */
@SpringBootTest
class DeliveryServiceTest {

    @Autowired
    private DeliveryService deliveries;
    @Autowired
    private ProductRepository products;
    @Autowired
    private UserAppRepository users;

    private Product product(String name, int stock) {
        return products.save(Product.builder().name(name + " " + UUID.randomUUID().toString().substring(0, 6))
                .price(new BigDecimal("1000")).stockQuantity(qty(stock)).build());
    }

    private UserApp manager() {
        String handle = "m" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Depot " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.DEPOT_MANAGER)).build());
    }

    @Test
    @DisplayName("every line of the note raises its product's stock, in the order of the note")
    void receptionDePlusieursProduitsEnUneFois() {
        Product sucre = product("Sucre", 6);
        Product riz = product("Riz", 50);

        List<SupplyResponse> booked = deliveries.receive(new CreateDeliveryRequest(null, List.of(
                new CreateDeliveryRequest.Line(sucre.getId(), null, qty(20), new BigDecimal("2400")),
                new CreateDeliveryRequest.Line(riz.getId(), null, qty(10), null))), manager());

        assertEquals(List.of(sucre.getId(), riz.getId()), booked.stream().map(s -> s.product().id()).toList());
        assertQuantity(26, products.findById(sucre.getId()).orElseThrow().getStockQuantity());
        assertQuantity(60, products.findById(riz.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    @DisplayName("one line refused: nothing of the note is booked")
    void uneLigneRefuseeRienNEstEnregistre() {
        Product sucre = product("Sucre", 6);

        assertThrows(ResourceNotFoundException.class, () -> deliveries.receive(new CreateDeliveryRequest(null, List.of(
                new CreateDeliveryRequest.Line(sucre.getId(), null, qty(20), null),
                new CreateDeliveryRequest.Line(Long.MAX_VALUE, null, qty(1), null))), manager()));

        assertQuantity(6, products.findById(sucre.getId()).orElseThrow().getStockQuantity());
    }
}
