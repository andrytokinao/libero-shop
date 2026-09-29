package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The weighted average cost. Every margin the application reports rests on these few lines, and
 * an error here is invisible: nothing crashes, the shop just believes it earns what it does not.
 */
class PurchaseCostingTest {

    private static BigDecimal ar(String amount) {
        return new BigDecimal(amount);
    }

    @Test
    @DisplayName("blends a receipt into the stock in proportion to the units")
    void weightsByUnits() {
        // 40 sacks at 10 000, then 10 at 11 000: the new sacks are a fifth of the shelf.
        assertEquals(ar("10200.00"),
                PurchaseCosting.averageAfterReceipt(40, ar("10000.00"), 10, ar("11000")));
    }

    @Test
    @DisplayName("two suppliers at two prices land between them, nearer the larger delivery")
    void twoSuppliers() {
        BigDecimal afterFirst = PurchaseCosting.averageAfterReceipt(0, null, 30, ar("600"));
        BigDecimal afterSecond = PurchaseCosting.averageAfterReceipt(30, afterFirst, 10, ar("800"));

        assertEquals(ar("600.00"), afterFirst);
        assertEquals(ar("650.00"), afterSecond);
    }

    @Test
    @DisplayName("the first known cost becomes the average")
    void firstCost() {
        assertEquals(ar("650.00"), PurchaseCosting.averageAfterReceipt(25, null, 10, ar("650")));
    }

    @Test
    @DisplayName("an empty shelf forgets the old cost: nothing is left to carry it")
    void emptyShelfResets() {
        assertEquals(ar("700.00"), PurchaseCosting.averageAfterReceipt(0, ar("500.00"), 10, ar("700")));
    }

    @Test
    @DisplayName("a receipt without a cost teaches nothing")
    void noCost() {
        assertEquals(ar("500.00"), PurchaseCosting.averageAfterReceipt(10, ar("500.00"), 10, null));
        assertNull(PurchaseCosting.averageAfterReceipt(10, null, 10, null));
    }

    @Test
    @DisplayName("rounds to two places, half up")
    void rounds() {
        // (1 × 100 + 2 × 101) / 3 = 100.666...
        assertEquals(ar("100.67"), PurchaseCosting.averageAfterReceipt(1, ar("100"), 2, ar("101")));
    }

    @Test
    @DisplayName("receiving raises the stock and moves the average in one step")
    void receive() {
        Product riz = Product.builder()
                .name("Riz 5kg")
                .price(ar("12500"))
                .stockQuantity(40)
                .averageCost(ar("10000.00"))
                .build();

        PurchaseCosting.receive(riz, 10, ar("11000"));

        assertEquals(50, riz.getStockQuantity());
        assertEquals(ar("10200.00"), riz.getAverageCost());
        assertEquals(ar("10200.00"), PurchaseCosting.costOfSale(riz));
    }

    @Test
    @DisplayName("the margin rate is taken on the selling price, and absent without sales")
    void marginRate() {
        assertEquals(ar("20.0"), PurchaseCosting.marginRate(ar("2500"), ar("12500")));
        assertNull(PurchaseCosting.marginRate(ar("0"), ar("0")));
    }
}
