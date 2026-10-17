package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.AdjustmentReason;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CreateStockAdjustmentRequest;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import com.houssen.liberoshop.web.dto.StockAdjustmentResponse;
import com.houssen.liberoshop.web.dto.SupplyResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.assertQuantity;
import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A receipt or a correction undone by an opposite movement, never deleted. */
@SpringBootTest
class MovementCancellationServiceTest {

    @Autowired
    private MovementCancellationService cancellations;
    @Autowired
    private StockService stock;
    @Autowired
    private ProductRepository products;
    @Autowired
    private StockMovementRepository movements;
    @Autowired
    private UserAppRepository users;

    private Product product(int stockQuantity) {
        return products.save(Product.builder().name("Riz " + UUID.randomUUID().toString().substring(0, 6))
                .price(new BigDecimal("1000")).stockQuantity(qty(stockQuantity)).build());
    }

    private UserApp manager() {
        String handle = "m" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Depot " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.DEPOT_MANAGER)).build());
    }

    @Test
    @DisplayName("a receipt cancelled: the stock goes back, the receipt stays, marked")
    void receptionAnnuleeParUnMouvementInverse() {
        Product riz = product(10);
        UserApp nadia = manager();
        SupplyResponse supply = stock.registerSupply(new CreateSupplyRequest(riz.getId(), null, qty(20), null), nadia);

        StockAdjustmentResponse opposite = cancellations.cancelSupply(supply.id(), nadia);

        assertQuantity(10, products.findById(riz.getId()).orElseThrow().getStockQuantity());
        assertEquals(AdjustmentReason.CANCELLATION, opposite.reason());
        assertQuantity(-20, opposite.quantity());
        assertTrue(opposite.comment().startsWith("Annulation de la reception du "));
        assertNotNull(movements.findById(supply.id()).orElseThrow().getCancelledAt());
    }

    @Test
    @DisplayName("a movement is cancelled once")
    void annulationUneSeuleFois() {
        Product riz = product(10);
        UserApp nadia = manager();
        SupplyResponse supply = stock.registerSupply(new CreateSupplyRequest(riz.getId(), null, qty(5), null), nadia);
        cancellations.cancelSupply(supply.id(), nadia);

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> cancellations.cancelSupply(supply.id(), nadia));
        assertEquals("MOVEMENT_ALREADY_CANCELLED", refused.code());
        assertQuantity(10, products.findById(riz.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    @DisplayName("goods already sold: the receipt cannot be taken back below zero")
    void annulationRefuseeSiLeStockDevenaitNegatif() {
        Product riz = product(0);
        UserApp nadia = manager();
        SupplyResponse supply = stock.registerSupply(new CreateSupplyRequest(riz.getId(), null, qty(20), null), nadia);
        stock.registerAdjustment(new CreateStockAdjustmentRequest(riz.getId(), AdjustmentReason.BREAKAGE, qty(15), null), nadia);

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> cancellations.cancelSupply(supply.id(), nadia));
        assertEquals("CANCELLATION_EXCEEDS_STOCK", refused.code());
    }

    @Test
    @DisplayName("a correction cancelled puts back what it took; a cancellation itself is final")
    void correctionAnnulee() {
        Product riz = product(10);
        UserApp nadia = manager();
        StockAdjustmentResponse broken = stock.registerAdjustment(
                new CreateStockAdjustmentRequest(riz.getId(), AdjustmentReason.BREAKAGE, qty(3), null), nadia);

        StockAdjustmentResponse opposite = cancellations.cancelAdjustment(broken.id(), nadia);

        assertQuantity(10, products.findById(riz.getId()).orElseThrow().getStockQuantity());
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> cancellations.cancelAdjustment(opposite.id(), nadia));
        assertEquals("CANCELLATION_FINAL", refused.code());
    }

    @Test
    @DisplayName("nobody picks the cancellation reason by hand")
    void motifAnnulationReserve() {
        Product riz = product(10);
        BusinessRuleException refused = assertThrows(BusinessRuleException.class, () -> stock.registerAdjustment(
                new CreateStockAdjustmentRequest(riz.getId(), AdjustmentReason.CANCELLATION, qty(1), "x"), manager()));
        assertEquals("ADJUSTMENT_REASON_RESERVED", refused.code());
    }
}
