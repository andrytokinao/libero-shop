package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.Sale;
import com.houssen.liberoshop.entity.SaleLine;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SaleLineRepository;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The searches behind the sale screens, against a real database: what is under test is the
 * queries -- folding, word matching, the rayon join, the limit, the best-seller ranking -- and a
 * mock would only hand back what the test wrote.
 *
 * <p>Pooled in-memory H2, as in {@code CostingServiceTest}, for the same check-constraint reason.
 */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:product-search;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductSearchServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    @Autowired
    private TestEntityManager db;
    @Autowired
    private ProductRepository products;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private SaleLineRepository saleLines;

    private ProductSearchService service;
    private UserApp fatima;
    private Category boissons;
    private Product riz;
    private Product cafe;
    private Product coca;
    private Product eau;
    private Product baguette;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        service = new ProductSearchService(products, saleLines,
                new CategoryService(categories, products), new BusinessCalendar(clock));

        fatima = db.persist(UserApp.builder()
                .fullName("Fatima")
                .username("fatima")
                .password("x")
                .enabled(true)
                .roles(EnumSet.of(RoleApp.CASHIER))
                .build());

        Category epicerie = db.persist(Category.builder().name("Épicerie").build());
        boissons = db.persist(Category.builder().name("Boissons").build());
        Category gazeux = db.persist(Category.builder().name("Gazeux").parent(boissons).build());

        riz = db.persist(product("Riz Makalioka 1kg", "6111234567812", epicerie));
        cafe = db.persist(product("Café Malagasy 250g", null, epicerie));
        coca = db.persist(product("Coca-Cola 1,5 L", "5449000000996", gazeux));
        eau = db.persist(product("Eau Vive 1,5L", null, boissons));
        baguette = db.persist(product("Baguette", "12", null));
        db.flush();
    }

    // -------------------------------------------------------------------- by code

    @Test
    @DisplayName("a scanned barcode finds its product, rayon included")
    void findsByBarcode() {
        ProductResponse found = service.findByCode("6111234567812");

        assertEquals("Riz Makalioka 1kg", found.name());
        assertEquals("Épicerie", found.category().name());
    }

    @Test
    @DisplayName("a short code is matched whole, never as the end of a longer barcode")
    void shortCodeIsAnIdentity() {
        assertEquals("Baguette", service.findByCode("12").name());
    }

    @Test
    @DisplayName("the blanks a keyboard wedge may add around a code are ignored")
    void trimsTheCode() {
        assertEquals("Baguette", service.findByCode("  12\t").name());
    }

    @Test
    @DisplayName("an unknown code is reported as not found, with the code in the message")
    void unknownCode() {
        ResourceNotFoundException error =
                assertThrows(ResourceNotFoundException.class, () -> service.findByCode("999"));
        assertEquals("Aucun produit ne porte le code « 999 ».", error.getMessage());
    }

    // ------------------------------------------------------------------- by words

    @Test
    @DisplayName("accents and case are ignored, on both sides")
    void foldsAccentsAndCase() {
        assertEquals(List.of("Café Malagasy 250g"), names(service.search("CAFE", null, 10)));
        assertEquals(List.of("Café Malagasy 250g"), names(service.search("café", null, 10)));
    }

    @Test
    @DisplayName("every word must match, in any order, punctuation counting as a space")
    void matchesEveryWord() {
        assertEquals(List.of("Coca-Cola 1,5 L"), names(service.search("1.5 coca", null, 10)));
        assertEquals(List.of(), names(service.search("coca riz", null, 10)));
    }

    @Test
    @DisplayName("a word may match the rayon's name instead of the product's")
    void matchesTheRayon() {
        assertEquals(List.of("Café Malagasy 250g", "Riz Makalioka 1kg"),
                names(service.search("epicerie", null, 10)));
    }

    @Test
    @DisplayName("a part of a barcode is enough to search, unlike a scan")
    void matchesPartOfABarcode() {
        assertEquals(List.of("Coca-Cola 1,5 L"), names(service.search("5449", null, 10)));
    }

    @Test
    @DisplayName("a rayon includes the rayons below it")
    void filtersOnABranch() {
        assertEquals(List.of("Coca-Cola 1,5 L", "Eau Vive 1,5L"),
                names(service.search("", boissons.getId(), 10)));
        assertEquals(List.of("Eau Vive 1,5L"), names(service.search("vive", boissons.getId(), 10)));
    }

    @Test
    @DisplayName("the answer is bounded and sorted by name")
    void isBounded() {
        assertEquals(List.of("Baguette", "Café Malagasy 250g"), names(service.search("", null, 2)));
        assertEquals(1, service.search("", null, 0).size(), "a limit below 1 is raised to 1");
    }

    @Test
    @DisplayName("a rename is searchable straight away")
    void followsARename() {
        riz.setName("Riz Vary Mena");
        db.flush();

        assertEquals(List.of("Riz Vary Mena"), names(service.search("mena", null, 10)));
        assertEquals(List.of(), names(service.search("makalioka", null, 10)));
    }

    // -------------------------------------------------------------- best sellers

    @Test
    @DisplayName("best sellers come first, most units first, then the rest by name")
    void featuresBestSellers() {
        sale(TODAY.atTime(9, 0), PaymentStatus.PAID, eau, 2, coca, 5);
        sale(TODAY.minusDays(3).atTime(9, 0), PaymentStatus.UNPAID, eau, 4);

        assertEquals(List.of("Eau Vive 1,5L", "Coca-Cola 1,5 L", "Baguette", "Café Malagasy 250g"),
                names(service.featured(4)));
    }

    @Test
    @DisplayName("cancelled orders and old sales do not make a best seller")
    void ignoresCancelledAndOldSales() {
        sale(TODAY.atTime(9, 0), PaymentStatus.CANCELLED, riz, 50);
        sale(TODAY.minusDays(60).atTime(9, 0), PaymentStatus.PAID, cafe, 50);
        sale(TODAY.atTime(10, 0), PaymentStatus.PAID, baguette, 1);

        assertEquals("Baguette", names(service.featured(5)).getFirst());
        assertEquals(5, service.featured(10).size(), "topped up with the whole catalogue");
    }

    // ------------------------------------------------------------------ fixtures

    private void sale(LocalDateTime when, PaymentStatus status, Object... lines) {
        Sale sale = Sale.builder()
                .saleDate(when)
                .paymentStatus(status)
                .seller(fatima)
                .totalAmount(BigDecimal.ZERO)
                .lines(new ArrayList<>())
                .build();
        for (int i = 0; i < lines.length; i += 2) {
            Product product = (Product) lines[i];
            sale.getLines().add(SaleLine.builder()
                    .sale(sale)
                    .product(product)
                    .quantity((Integer) lines[i + 1])
                    .unitPrice(product.getPrice())
                    .build());
        }
        sale.calculateTotal();
        db.persist(sale);
        db.flush();
    }

    private static Product product(String name, String barcode, Category category) {
        return Product.builder()
                .name(name)
                .price(BigDecimal.valueOf(1000))
                .stockQuantity(10)
                .barcode(barcode)
                .category(category)
                .build();
    }

    private static List<String> names(List<ProductResponse> found) {
        return found.stream().map(ProductResponse::name).toList();
    }
}
