package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.StockOutputPageResponse;
import com.houssen.liberoshop.web.dto.StockOutputResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.assertQuantity;
import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outputs read a page at a time: the list grows by a row per line sold, so the screen is
 * handed a page and the sums of the whole, never the whole.
 */
@SpringBootTest
class StockOutputPagingTest {

    @Autowired
    private StockService stockService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private ProductRepository products;
    @Autowired
    private UserAppRepository users;

    /** Found in no other test's data: what the searches below are narrowed to. */
    private String marker;
    private UserApp cashier;

    @BeforeEach
    void sevenSalesOfOneArticle() {
        marker = "zq" + UUID.randomUUID().toString().substring(0, 8);
        String handle = "c" + UUID.randomUUID().toString().substring(0, 8);
        cashier = users.save(UserApp.builder().fullName("Caisse " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.CASHIER)).build());
        Product soap = products.save(Product.builder().name("Savon " + marker.toUpperCase())
                .price(new BigDecimal("1500")).stockQuantity(qty(100)).build());
        for (int sale = 1; sale <= 7; sale++) {
            sell(soap, sale, "Client " + sale);
        }
    }

    private void sell(Product product, int quantity, String client) {
        saleService.checkout(new CreateSaleRequest(client, PaymentStatus.PAID, PaymentMethod.CASH,
                List.of(new CreateSaleRequest.Line(product.getId(), qty(quantity)))), cashier);
    }

    @Test
    @DisplayName("Trois par page : trois pages, les plus recentes d'abord, sans doublon ni oubli")
    void pagesCoverEverythingOnce() {
        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            StockOutputPageResponse found = stockService.findOutputs(marker, page, 3);
            assertEquals(7, found.total());
            assertEquals(page, found.page());
            assertEquals(page < 2, found.hasMore());
            found.items().stream().map(StockOutputResponse::id).forEach(seen::add);
        }

        assertEquals(7, seen.size());
        assertEquals(7, seen.stream().distinct().count());
        // Sold in that order, so the newest -- the highest id -- comes first.
        assertEquals(seen.stream().sorted((a, b) -> Long.compare(b, a)).toList(), seen);
    }

    @Test
    @DisplayName("Les totaux portent sur toute la recherche, pas sur la page affichee")
    void totalsCoverTheWholeAnswer() {
        StockOutputPageResponse firstPage = stockService.findOutputs(marker, 0, 2);

        assertEquals(2, firstPage.items().size());
        // 1 + 2 + ... + 7 units, at 1 500 each.
        assertQuantity(28, firstPage.totalQuantity());
        assertEquals(0, new BigDecimal("42000").compareTo(firstPage.totalValue()));
    }

    @Test
    @DisplayName("La recherche ignore la casse et porte aussi sur le client")
    void searchIsCaseInsensitive() {
        assertEquals(7, stockService.findOutputs(marker.toUpperCase(), 0, 50).total());

        String client = "Acheteur" + marker;
        sell(products.save(Product.builder().name("Sucre " + UUID.randomUUID())
                .price(new BigDecimal("900")).stockQuantity(qty(10)).build()), 1, client);

        StockOutputPageResponse byClient = stockService.findOutputs("acheteur" + marker, 0, 50);
        assertEquals(1, byClient.total());
        assertEquals(client, byClient.items().getFirst().invoice().clientName());
    }

    @Test
    @DisplayName("Un % ou un _ tape dans la recherche est cherche tel quel")
    void likeCharactersAreLiteral() {
        assertEquals(0, stockService.findOutputs(marker + "%", 0, 50).total());
        assertEquals(0, stockService.findOutputs(marker.substring(0, 4) + "_" + marker.substring(5), 0, 50).total());
        assertNull(StockService.likePattern("   "));
        assertEquals("%50\\%\\_a\\\\%", StockService.likePattern(" 50%_A\\ "));
    }

    @Test
    @DisplayName("Sans recherche : tout, une page a la fois ; une taille demesuree est bornee")
    void withoutSearch() {
        StockOutputPageResponse everything = stockService.findOutputs(null, 0, 100_000);

        assertTrue(everything.total() >= 7);
        assertEquals(StockService.MAX_PAGE_SIZE, everything.size());
        assertTrue(everything.items().size() <= StockService.MAX_PAGE_SIZE);
        assertFalse(stockService.findOutputs(marker, 5, 50).hasMore());
    }
}
