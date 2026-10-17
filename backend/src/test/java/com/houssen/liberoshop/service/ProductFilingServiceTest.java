package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Products filed in a rayon, one at a time or a whole rayon at once. */
@SpringBootTest
class ProductFilingServiceTest {

    @Autowired
    private ProductFilingService filing;
    @Autowired
    private ProductRepository products;
    @Autowired
    private CategoryRepository categories;

    private Category rayon(String name) {
        return categories.save(Category.builder().name(name + " " + UUID.randomUUID().toString().substring(0, 6)).build());
    }

    private Product product(Category category) {
        return products.save(Product.builder().name("Produit " + UUID.randomUUID().toString().substring(0, 6))
                .price(new BigDecimal("1000")).stockQuantity(qty(5)).category(category).build());
    }

    private Long rayonOf(Product product) {
        return products.findAllWithCategoryByIdIn(List.of(product.getId())).getFirst().getCategory().getId();
    }

    @Test
    @DisplayName("a product changes rayon; one already there is not counted")
    void produitChangeDeRayon() {
        Category epicerie = rayon("Epicerie");
        Category boissons = rayon("Boissons");
        Product jus = product(epicerie);
        Product eau = product(boissons);

        assertEquals(1, filing.file(List.of(jus.getId(), eau.getId()), boissons.getId()));
        assertEquals(boissons.getId(), rayonOf(jus));
    }

    @Test
    @DisplayName("every product of a rayon moved to another; the first rayon stays")
    void toutLeRayonDeplace() {
        Category ancien = rayon("Ancien");
        Category nouveau = rayon("Nouveau");
        Product a = product(ancien);
        Product b = product(ancien);

        assertEquals(2, filing.moveAll(ancien.getId(), nouveau.getId()));
        assertEquals(nouveau.getId(), rayonOf(a));
        assertEquals(nouveau.getId(), rayonOf(b));
        assertEquals(true, categories.existsById(ancien.getId()));
    }

    @Test
    @DisplayName("an unknown product moves nothing, and the same rayon is refused")
    void refus() {
        Category epicerie = rayon("Epicerie");
        Category boissons = rayon("Boissons");
        Product jus = product(epicerie);

        assertThrows(ResourceNotFoundException.class,
                () -> filing.file(List.of(jus.getId(), Long.MAX_VALUE), boissons.getId()));
        assertEquals(epicerie.getId(), rayonOf(jus));
        BusinessRuleException same = assertThrows(BusinessRuleException.class,
                () -> filing.moveAll(epicerie.getId(), epicerie.getId()));
        assertEquals("CATEGORY_SAME_TARGET", same.code());
    }
}
