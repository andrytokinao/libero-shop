package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The dashboard totals, summed by the database, say what adding every product up says. */
@SpringBootTest
class StockTotalsTest {

    @Autowired
    private CatalogService catalog;
    @Autowired
    private ProductRepository products;

    @Test
    @DisplayName("valeur, unites et alertes comptees par la base = la somme produit par produit")
    void totauxCommeLaSomme() {
        // A price times a fractional stock, so each product's value needs its own rounding.
        products.save(Product.builder().name("Riz " + UUID.randomUUID()).price(new BigDecimal("333.33"))
                .stockQuantity(new BigDecimal("2.505")).build());
        products.save(Product.builder().name("Sel " + UUID.randomUUID()).price(new BigDecimal("500"))
                .stockQuantity(qty(1)).lowStockThreshold(qty(5)).build());

        List<Product> all = products.findAll();
        BigDecimal value = all.stream()
                .map(p -> p.getPrice().multiply(p.getStockQuantity()).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal units = all.stream().map(Product::getStockQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        long inAlert = all.stream().filter(StockPolicy::isLowStock).count();

        assertEquals(0, value.compareTo(catalog.totalStockValue()));
        assertEquals(0, units.compareTo(catalog.totalUnitsInStock()));
        assertEquals(inAlert, catalog.countLowStock());
    }
}
