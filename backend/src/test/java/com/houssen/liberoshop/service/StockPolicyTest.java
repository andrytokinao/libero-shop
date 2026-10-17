package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a product is "en alerte". */
class StockPolicyTest {

    private static Product withStock(String stock, String threshold) {
        return Product.builder().name("Riz").price(BigDecimal.TEN).stockQuantity(new BigDecimal(stock))
                .lowStockThreshold(threshold == null ? null : new BigDecimal(threshold)).build();
    }

    @Test
    void sansSeuilPropreLeSeuilCommunDeDixSApplique() {
        assertTrue(StockPolicy.isLowStock(withStock("9", null)));
        assertFalse(StockPolicy.isLowStock(withStock("10", null)));
        assertEquals(StockPolicy.LOW_STOCK_THRESHOLD, StockPolicy.thresholdOf(withStock("9", null)));
    }

    @Test
    void unSeuilProprePlusHautMetEnAlertePlusTot() {
        assertTrue(StockPolicy.isLowStock(withStock("30", "50")));
    }

    @Test
    void unSeuilPropreBasLaisseUnPetitStockHorsAlerte() {
        assertFalse(StockPolicy.isLowStock(withStock("3", "2")));
        assertEquals(new BigDecimal("2"), StockPolicy.thresholdOf(withStock("3", "2")));
    }

    @Test
    void seuilZeroNAlerteJamais() {
        assertFalse(StockPolicy.isLowStock(withStock("0", "0")));
    }
}
