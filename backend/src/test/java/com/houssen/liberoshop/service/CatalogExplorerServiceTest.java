package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.FavoriteRepository;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SaleLineRepository;
import com.houssen.liberoshop.web.dto.CatalogEntry;
import com.houssen.liberoshop.web.dto.CatalogPageResponse;
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
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalogue as a file explorer, against a real database: folders first, then the files of
 * the folder -- and what each folder says it holds.
 *
 * <pre>
 * Épicerie            (Sel)
 *   └ Riz             (Riz vrac, Riz Makalioka)
 * Boissons            (Eau)
 * (no rayon)          (Baguette)
 * </pre>
 */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:catalog-explorer;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CatalogExplorerServiceTest {

    @Autowired
    private TestEntityManager db;
    @Autowired
    private ProductRepository products;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private ProductPackagingRepository packagings;
    @Autowired
    private SaleLineRepository saleLines;
    @Autowired
    private FavoriteRepository favoriteRows;

    private CatalogExplorerService explorer;
    private FavoriteService favorites;
    private UserApp fatima;
    private Category epicerie;
    private Category riz;
    private Category boissons;
    private Product eau;

    @BeforeEach
    void setUp() {
        BusinessCalendar calendar = new BusinessCalendar(Clock.fixed(
                LocalDate.of(2026, 10, 2).atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault()));
        CategoryService categoryService = new CategoryService(categories, products);
        favorites = new FavoriteService(favoriteRows, products, categories, categoryService, calendar);
        ProductSearchService search = new ProductSearchService(products, packagings, saleLines,
                categoryService, favorites, calendar);
        explorer = new CatalogExplorerService(search, categoryService, favorites, products);

        fatima = db.persist(UserApp.builder().fullName("Fatima").username("fatima").password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.CASHIER)).build());
        epicerie = db.persist(Category.builder().name("Épicerie").build());
        riz = db.persist(Category.builder().name("Riz").parent(epicerie).build());
        boissons = db.persist(Category.builder().name("Boissons").build());

        db.persist(product("Sel 1kg", epicerie));
        db.persist(product("Riz vrac", riz));
        db.persist(product("Riz Makalioka", riz));
        eau = db.persist(product("Eau Vive", boissons));
        db.persist(product("Baguette", null));
        db.flush();
    }

    private static Product product(String name, Category category) {
        return Product.builder().name(name).price(BigDecimal.valueOf(1000)).stockQuantity(qty(10))
                .category(category).build();
    }

    private static List<String> folders(CatalogPageResponse page) {
        return page.entries().stream()
                .flatMap(entry -> entry instanceof CatalogEntry.Folder folder
                        ? Stream.of(folder.rayon().name() + " " + folder.folders() + "/" + folder.products())
                        : Stream.empty())
                .toList();
    }

    private static List<String> files(CatalogPageResponse page) {
        return page.entries().stream()
                .flatMap(entry -> entry instanceof CatalogEntry.Item item
                        ? Stream.of(item.product().name())
                        : Stream.empty())
                .toList();
    }

    @Test
    @DisplayName("the top shows the main rayons, each counting what is below it, then the best sellers")
    void topOfTheShop() {
        CatalogPageResponse top = explorer.explore(null, false, "", null, 0, 40);

        // Épicerie: one sub-rayon, three products in all (Sel + the two rices).
        assertEquals(List.of("Boissons 0/1", "Épicerie 1/3"), folders(top));
        assertEquals(5, files(top).size(), "a shop too new for best sellers shows its catalogue by name");
        assertFalse(top.hasMore());
    }

    @Test
    @DisplayName("a folder lists its sub-folders, then only what is filed directly in it")
    void insideAFolder() {
        CatalogPageResponse inside = explorer.explore(epicerie.getId(), false, "", null, 0, 40);

        assertEquals(List.of("Riz 0/2"), folders(inside));
        assertEquals(List.of("Sel 1kg"), files(inside));
        assertEquals(1, inside.totalProducts());
    }

    @Test
    @DisplayName("'all' shows every product below the folder, its sub-folders still on top")
    void wholeBranch() {
        CatalogPageResponse all = explorer.explore(epicerie.getId(), true, "", null, 0, 40);

        assertEquals(List.of("Riz 0/2"), folders(all));
        assertEquals(List.of("Riz Makalioka", "Riz vrac", "Sel 1kg"), files(all));
    }

    @Test
    @DisplayName("words find the matching rayons as folders, then the matching products")
    void search() {
        CatalogPageResponse found = explorer.explore(null, false, "riz", null, 0, 40);

        assertEquals(List.of("Riz 0/2"), folders(found));
        assertEquals(List.of("Riz Makalioka", "Riz vrac"), files(found));
    }

    @Test
    @DisplayName("inside a folder, a search only offers the rayons below it")
    void searchWithinAFolder() {
        CatalogPageResponse found = explorer.explore(boissons.getId(), false, "riz", null, 0, 40);

        assertEquals(List.of(), folders(found));
        assertEquals(List.of(), files(found));
    }

    @Test
    @DisplayName("the favourites are the pinned rayons as folders, then the pinned products")
    void favourites() {
        favorites.pinCategory(fatima, riz.getId());
        favorites.pinProduct(fatima, eau.getId());

        CatalogPageResponse mine = explorer.explore(null, false, "", fatima, 0, 40);

        assertEquals(List.of("Riz 0/2"), folders(mine));
        assertEquals(List.of("Eau Vive"), files(mine));
    }

    @Test
    @DisplayName("the folders come with the first page only, and the products are paged")
    void paging() {
        CatalogPageResponse first = explorer.explore(epicerie.getId(), true, "", null, 0, 2);
        CatalogPageResponse second = explorer.explore(epicerie.getId(), true, "", null, 1, 2);

        assertEquals(1, folders(first).size());
        assertTrue(first.hasMore());
        assertEquals(3, first.totalProducts());
        assertEquals(List.of(), folders(second));
        assertEquals(List.of("Sel 1kg"), files(second));
        assertFalse(second.hasMore());
    }
}
