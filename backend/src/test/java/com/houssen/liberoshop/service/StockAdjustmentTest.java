package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.AdjustmentReason;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CreateStockAdjustmentRequest;
import com.houssen.liberoshop.web.dto.StockAdjustmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.assertQuantity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stock corrected by hand: bottles broken, a shelf counted.
 *
 * <p>A loss is said as what left, a count as what remains, and either way the movement kept is
 * the difference -- signed, named and attributed -- so the stock can still be explained line by
 * line afterwards.
 */
@SpringBootTest
class StockAdjustmentTest {

    @Autowired
    private StockService stockService;
    @Autowired
    private ProductRepository products;
    @Autowired
    private UserAppRepository users;

    private Product juice;
    private UserApp manager;

    @BeforeEach
    void fortyBottles() {
        juice = products.save(Product.builder()
                .name("Jus " + UUID.randomUUID().toString().substring(0, 6))
                .unit("bouteille")
                .price(new BigDecimal("3000"))
                .averageCost(new BigDecimal("2000"))
                .stockQuantity(new BigDecimal("40"))
                .build());
        String handle = "m" + UUID.randomUUID().toString().substring(0, 8);
        manager = users.save(UserApp.builder().fullName("Gestion " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.DEPOT_MANAGER)).build());
    }

    private StockAdjustmentResponse adjust(AdjustmentReason reason, String quantity, String comment) {
        return stockService.registerAdjustment(
                new CreateStockAdjustmentRequest(juice.getId(), reason, new BigDecimal(quantity), comment),
                manager);
    }

    private Product reloaded() {
        return products.findById(juice.getId()).orElseThrow();
    }

    @Test
    @DisplayName("3 bouteilles cassees : le stock baisse de 3, la perte est chiffree au cout d'achat")
    void breakageTakesGoodsOut() {
        StockAdjustmentResponse adjustment = adjust(AdjustmentReason.BREAKAGE, "3", " carton  tombe ");

        assertQuantity(37, reloaded().getStockQuantity());
        assertQuantity(-3, adjustment.quantity());
        assertEquals(AdjustmentReason.BREAKAGE, adjustment.reason());
        assertEquals("carton tombe", adjustment.comment());
        assertEquals(0, new BigDecimal("-6000").compareTo(adjustment.costValue()));
        assertEquals(manager.getId(), adjustment.performedBy().id());
        // What the remaining bottles cost has not changed.
        assertEquals(0, new BigDecimal("2000").compareTo(reloaded().getAverageCost()));
    }

    @Test
    @DisplayName("Inventaire a 37 : l'ecart de -3 est calcule par le serveur")
    void aCountBelowTheStock() {
        StockAdjustmentResponse adjustment = adjust(AdjustmentReason.INVENTORY_COUNT, "37", null);

        assertQuantity(37, reloaded().getStockQuantity());
        assertQuantity(-3, adjustment.quantity());
        assertNull(adjustment.comment());
    }

    @Test
    @DisplayName("Inventaire a 42 : un compte peut aussi trouver plus que prevu")
    void aCountAboveTheStock() {
        StockAdjustmentResponse adjustment = adjust(AdjustmentReason.INVENTORY_COUNT, "42", null);

        assertQuantity(42, reloaded().getStockQuantity());
        assertQuantity(2, adjustment.quantity());
    }

    @Test
    @DisplayName("Inventaire a zero : le rayon est vide, le stock aussi")
    void aCountOfZero() {
        adjust(AdjustmentReason.INVENTORY_COUNT, "0", null);

        assertQuantity(0, reloaded().getStockQuantity());
    }

    @Test
    @DisplayName("Un inventaire qui confirme le stock n'ecrit aucun mouvement")
    void aCountThatAgreesIsRefused() {
        int before = stockService.findAdjustments().size();

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> adjust(AdjustmentReason.INVENTORY_COUNT, "40", null));

        assertEquals("ADJUSTMENT_NOTHING_TO_CORRECT", refused.code());
        assertEquals(before, stockService.findAdjustments().size());
    }

    @Test
    @DisplayName("On ne perd pas plus que le stock, et le stock ne devient jamais negatif")
    void aLossBeyondTheStockIsRefused() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> adjust(AdjustmentReason.THEFT, "41", null));

        assertEquals("ADJUSTMENT_EXCEEDS_STOCK", refused.code());
        assertQuantity(40, reloaded().getStockQuantity());
    }

    @Test
    @DisplayName("Une perte de zero n'est pas une perte")
    void aLossOfNothingIsRefused() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> adjust(AdjustmentReason.EXPIRED, "0", null));

        assertEquals("ADJUSTMENT_QUANTITY_INVALID", refused.code());
    }

    @Test
    @DisplayName("Le motif Autre doit etre explique")
    void otherNeedsAComment() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> adjust(AdjustmentReason.OTHER, "1", "  "));

        assertEquals("ADJUSTMENT_COMMENT_REQUIRED", refused.code());
        assertQuantity(40, reloaded().getStockQuantity());
    }

    @Test
    @DisplayName("Les corrections se relisent, la plus recente d'abord")
    void correctionsAreListed() {
        adjust(AdjustmentReason.BREAKAGE, "1", null);
        StockAdjustmentResponse last = adjust(AdjustmentReason.OTHER, "2", "degustation");

        StockAdjustmentResponse first = stockService.findAdjustments().get(0);

        assertEquals(last.id(), first.id());
        assertTrue(stockService.findAdjustments().stream()
                .filter(a -> a.product().id().equals(juice.getId())).count() >= 2);
    }
}
