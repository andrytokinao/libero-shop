package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.AdjustmentReason;
import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.CreateInventoryRequest;
import com.houssen.liberoshop.web.dto.CreateInventoryRequest.Line;
import com.houssen.liberoshop.web.dto.StockAdjustmentResponse;
import org.junit.jupiter.api.BeforeEach;
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

/** A rayon counted in one go: a correction per product found different, all or none. */
@SpringBootTest
class InventoryServiceTest {

    @Autowired
    private InventoryService inventories;
    @Autowired
    private ProductRepository products;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private UserAppRepository users;

    private Category boissons;
    private Product eau;
    private Product jus;
    private UserApp nadia;

    @BeforeEach
    void rayonDeDeuxProduits() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        boissons = categories.save(Category.builder().name("Boissons " + suffix).build());
        eau = products.save(Product.builder().name("Eau " + suffix).price(new BigDecimal("1200"))
                .stockQuantity(qty(10)).category(boissons).build());
        jus = products.save(Product.builder().name("Jus " + suffix).price(new BigDecimal("4800"))
                .stockQuantity(qty(6)).category(boissons).build());
        nadia = users.save(UserApp.builder().fullName("Nadia " + suffix).username("n" + suffix).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.DEPOT_MANAGER)).build());
    }

    private BigDecimal stockOf(Product product) {
        return products.findById(product.getId()).orElseThrow().getStockQuantity();
    }

    @Test
    @DisplayName("seul le produit trouve different est corrige, au nom du rayon")
    void seulLEcartEstCorrige() {
        List<StockAdjustmentResponse> written = inventories.count(new CreateInventoryRequest(boissons.getId(),
                List.of(new Line(eau.getId(), qty(8)), new Line(jus.getId(), qty(6)))), nadia);

        assertEquals(1, written.size());
        assertQuantity(-2, written.get(0).quantity());
        assertEquals(AdjustmentReason.INVENTORY_COUNT, written.get(0).reason());
        assertEquals("Inventaire du rayon " + boissons.getName(), written.get(0).comment());
        assertQuantity(8, stockOf(eau));
        assertQuantity(6, stockOf(jus));
    }

    @Test
    @DisplayName("une ligne refusee et rien n'est ecrit")
    void toutOuRien() {
        assertThrows(ResourceNotFoundException.class, () -> inventories.count(new CreateInventoryRequest(
                boissons.getId(), List.of(new Line(eau.getId(), qty(3)), new Line(Long.MAX_VALUE, qty(1)))), nadia));

        assertQuantity(10, stockOf(eau));
    }

    @Test
    @DisplayName("un meme produit compte deux fois est refuse")
    void produitCompteDeuxFois() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class, () -> inventories.count(
                new CreateInventoryRequest(null, List.of(new Line(eau.getId(), qty(3)), new Line(eau.getId(), qty(4)))),
                nadia));

        assertEquals("INVENTORY_PRODUCT_TWICE", refused.code());
        assertQuantity(10, stockOf(eau));
    }

    @Test
    @DisplayName("un rayon trouve vide se compte a zero")
    void rayonTrouveVide() {
        inventories.count(new CreateInventoryRequest(boissons.getId(),
                List.of(new Line(eau.getId(), qty(0)), new Line(jus.getId(), qty(0)))), nadia);

        assertQuantity(0, stockOf(eau));
        assertQuantity(0, stockOf(jus));
    }
}
