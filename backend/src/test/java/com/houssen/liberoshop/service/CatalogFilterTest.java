package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.web.dto.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The product list filtered by the database: words typed, rayon and its sub-rayons, low stock. */
@SpringBootTest
class CatalogFilterTest {

    @Autowired
    private CatalogService catalog;
    @Autowired
    private ProductRepository products;
    @Autowired
    private CategoryRepository categories;

    private String tag;
    private Category boissons;
    private Category jus;
    private String barcode;

    private Product product(String name, String barcode, Category rayon, int stock, Integer threshold) {
        return products.save(Product.builder().name(name + " " + tag).barcode(barcode).category(rayon)
                .price(new BigDecimal("1000")).stockQuantity(qty(stock))
                .lowStockThreshold(threshold == null ? null : qty(threshold)).build());
    }

    private List<String> names(String search, Long rayon, boolean lowOnly) {
        return catalog.findProducts(search, rayon, lowOnly).stream()
                .map(ProductResponse::name).filter(name -> name.endsWith(tag)).toList();
    }

    @BeforeEach
    void rayons() {
        tag = UUID.randomUUID().toString().substring(0, 8);
        boissons = categories.save(Category.builder().name("Boissons " + tag).build());
        jus = categories.save(Category.builder().name("Jus " + tag).parent(boissons).build());
        barcode = "54" + (System.nanoTime() % 100_000_000_000L);
        product("Coca-Cola 1,5 L", barcode, boissons, 30, null);
        product("Jus d'orange", null, jus, 3, null);
        product("Riz 50% brise", null, null, 8, 5);
    }

    @Test
    @DisplayName("les mots tapes cherchent dans le nom, le code-barres et le rayon, sans tenir compte des majuscules")
    void rechercheParMots() {
        assertEquals(List.of("Coca-Cola 1,5 L " + tag), names("  COCA ", null, false));
        assertEquals(List.of("Coca-Cola 1,5 L " + tag), names(barcode.substring(0, 9), null, false));
        assertEquals(List.of("Jus d'orange " + tag), names("jus " + tag, null, false));
    }

    @Test
    @DisplayName("50% cherche le texte 50%, pas n'importe quoi")
    void caracteresSpeciauxCherchesTelsQuels() {
        assertEquals(List.of("Riz 50% brise " + tag), names("50%", null, false));
    }

    @Test
    @DisplayName("un rayon montre aussi ses sous-rayons ; le stock bas suit le seuil de chaque produit")
    void rayonEtStockBas() {
        assertEquals(List.of("Coca-Cola 1,5 L " + tag, "Jus d'orange " + tag), names(null, boissons.getId(), false));
        assertEquals(List.of("Jus d'orange " + tag), names(null, jus.getId(), false));
        assertEquals(List.of("Jus d'orange " + tag), names(null, null, true),
                "3 jus sous le seuil commun ; 8 sacs de riz au-dessus de leur seuil de 5");
    }
}
