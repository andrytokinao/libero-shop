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
import com.houssen.liberoshop.web.dto.CreateProductRequest;
import com.houssen.liberoshop.web.dto.CreateStockAdjustmentRequest;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import com.houssen.liberoshop.web.dto.ProductMovementResponse;
import com.houssen.liberoshop.web.dto.ProductResponse;
import com.houssen.liberoshop.web.dto.SupplyResponse;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A catalogue kept by hand, without an import file: a product is typed in, its stock is added
 * to, and every unit that ever moved stays in its history.
 */
@SpringBootTest
class ProductByHandTest {

    @Autowired
    private ProductEditService productEditService;
    @Autowired
    private StockService stockService;
    @Autowired
    private CostingService costingService;
    @Autowired
    private ProductRepository products;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private UserAppRepository users;

    private UserApp manager;
    private String name;

    @BeforeEach
    void aManager() {
        String handle = "m" + UUID.randomUUID().toString().substring(0, 8);
        manager = users.save(UserApp.builder().fullName("Gestion " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.DEPOT_MANAGER)).build());
        name = "Savon " + UUID.randomUUID().toString().substring(0, 6);
    }

    private ProductResponse create(String barcode, Long categoryId, String cost, String stock) {
        return productEditService.create(new CreateProductRequest("  " + name + " ", new BigDecimal("1500"),
                " piece ", barcode, categoryId, cost == null ? null : new BigDecimal(cost),
                stock == null ? null : new BigDecimal(stock)), manager);
    }

    private List<ProductMovementResponse> historyOf(Long productId) {
        return stockService.findMovementsOf(productId, 0, 30).items();
    }

    @Test
    @DisplayName("Un produit saisi a la main : sa fiche, son rayon, son cout, et son stock de depart dans l'historique")
    void createdWithItsStartingStock() {
        Category rayon = categories.save(Category.builder().name("Hygiene " + UUID.randomUUID()).build());

        ProductResponse created = create(null, rayon.getId(), "1000", "24");

        assertEquals(name, created.name());
        assertEquals("piece", created.unit());
        assertEquals(rayon.getId(), created.category().id());
        assertQuantity(24, created.stockQuantity());
        assertEquals(new BigDecimal("1000.00"), products.findById(created.id()).orElseThrow().getAverageCost());

        List<ProductMovementResponse> history = historyOf(created.id());
        assertEquals(1, history.size());
        ProductMovementResponse entry = history.getFirst();
        assertEquals("SUPPLY", entry.type());
        assertQuantity(24, entry.quantity());
        assertNull(entry.supplierName());
        assertEquals(new BigDecimal("1000.00"), entry.unitCost());
        assertEquals(manager.getFullName(), entry.performedBy());
    }

    @Test
    @DisplayName("Sans stock de depart, aucun mouvement n'est ecrit, et le cout saisi est quand meme retenu")
    void createdEmpty() {
        ProductResponse created = create(null, null, "1000", null);

        assertQuantity(0, created.stockQuantity());
        assertTrue(historyOf(created.id()).isEmpty());
        assertEquals(new BigDecimal("1000.00"), costingService.productCostOf(created.id()).averageCost());
    }

    @Test
    @DisplayName("Un nom ou un code-barres deja pris est refuse")
    void refusesWhatIsTaken() {
        String barcode = "bc" + UUID.randomUUID().toString().substring(0, 10);
        create(barcode, null, null, null);

        BusinessRuleException sameName = assertThrows(BusinessRuleException.class,
                () -> productEditService.create(new CreateProductRequest(name.toUpperCase(),
                        new BigDecimal("1500"), null, null, null, null, null), manager));
        assertEquals("PRODUCT_NAME_TAKEN", sameName.code());

        BusinessRuleException sameCode = assertThrows(BusinessRuleException.class,
                () -> productEditService.create(new CreateProductRequest(name + " bis",
                        new BigDecimal("1500"), null, barcode, null, null, null), manager));
        assertEquals("BARCODE_TAKEN", sameCode.code());
    }

    @Test
    @DisplayName("Du stock ajoute sans fournisseur : le stock monte, le cout moyen suit, l'entree est gardee")
    void stockAddedWithoutASupplier() {
        ProductResponse created = create(null, null, "1000", "10");

        SupplyResponse supply = stockService.registerSupply(
                new CreateSupplyRequest(created.id(), null, new BigDecimal("10"), new BigDecimal("1200")),
                manager);

        assertTrue(supply.withoutSupplier());
        assertNull(supply.supplier());
        Product reloaded = products.findById(created.id()).orElseThrow();
        assertQuantity(20, reloaded.getStockQuantity());
        assertEquals(new BigDecimal("1100.00"), reloaded.getAverageCost());
        assertEquals(2, historyOf(created.id()).size());
    }

    @Test
    @DisplayName("L'historique d'un produit melange entrees et corrections, la plus recente d'abord, signees")
    void historyKeepsEveryMovement() {
        ProductResponse created = create(null, null, null, "10");
        stockService.registerSupply(
                new CreateSupplyRequest(created.id(), null, new BigDecimal("5"), null), manager);
        stockService.registerAdjustment(new CreateStockAdjustmentRequest(created.id(),
                AdjustmentReason.BREAKAGE, new BigDecimal("2"), "tombe"), manager);

        ProductMovementResponse.Page page = stockService.findMovementsOf(created.id(), 0, 2);

        assertEquals(3, page.total());
        assertTrue(page.hasMore());
        ProductMovementResponse latest = page.items().getFirst();
        assertEquals("ADJUSTMENT", latest.type());
        assertQuantity(-2, latest.quantity());
        assertEquals(AdjustmentReason.BREAKAGE, latest.reason());
        assertEquals("tombe", latest.comment());
        assertQuantity(13, products.findById(created.id()).orElseThrow().getStockQuantity());
    }
}
