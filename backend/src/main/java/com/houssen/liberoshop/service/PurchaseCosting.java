package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Where "what did this stock cost us" is defined, once: the weighted average cost method.
 *
 * <p>Every receipt that states a unit cost blends it into the product's average in proportion
 * to the units it brings, and every sale freezes the average of that moment on its line. Two
 * suppliers at two prices, or one supplier raising theirs, therefore move the average by
 * exactly as much as their share of the shelf -- which is what the stock is really worth, and
 * what a sale really consumed.
 *
 * <p>The method is chosen here and nowhere else. Receipts go through {@link #receive} and sales
 * through {@link #costOfSale}; moving to first-in-first-out later means changing these two and
 * reading the lots from the {@code Supply} rows, which already carry their own cost, date and
 * supplier. Neither the services nor the reports would have to know.
 */
public final class PurchaseCosting {

    /** Ariary have no subdivision in use; two places match the price columns and absorb rounding. */
    public static final int COST_SCALE = 2;

    private PurchaseCosting() {
    }

    /**
     * Takes goods into the stock: raises the quantity and blends their cost into the average.
     *
     * <p>The only way stock is received, so that no path can raise a quantity and forget the
     * cost that goes with it.
     *
     * @param unitCost what one unit of this receipt cost, or null when it was not given -- the
     *                 units still enter the stock, and the average is left as it was
     */
    public static void receive(Product product, int quantity, BigDecimal unitCost) {
        product.setAverageCost(averageAfterReceipt(
                product.getStockQuantity(), product.getAverageCost(), quantity, unitCost));
        product.adjustStock(quantity);
    }

    /**
     * The average cost once a receipt is on the shelf.
     *
     * <p>Four cases, in this order:
     * <ul>
     *   <li>no cost given: nothing is learned, the average stands;</li>
     *   <li>no average yet, or nothing left on the shelf: the receipt's cost is the only fact,
     *       and it becomes the average -- units of unknown or exhausted cost cannot weigh;</li>
     *   <li>nothing received: the average stands;</li>
     *   <li>otherwise: {@code (stock × average + received × cost) / (stock + received)}.</li>
     * </ul>
     */
    public static BigDecimal averageAfterReceipt(int stockBefore, BigDecimal averageBefore,
                                                 int received, BigDecimal unitCost) {
        if (unitCost == null) {
            return averageBefore;
        }
        if (averageBefore == null || stockBefore <= 0) {
            return scaled(unitCost);
        }
        if (received <= 0) {
            return averageBefore;
        }
        BigDecimal valueBefore = averageBefore.multiply(BigDecimal.valueOf(stockBefore));
        BigDecimal valueReceived = unitCost.multiply(BigDecimal.valueOf(received));
        return valueBefore.add(valueReceived)
                .divide(BigDecimal.valueOf((long) stockBefore + received), COST_SCALE,
                        RoundingMode.HALF_UP);
    }

    /**
     * The cost to freeze on a sale line: the average of the stock the units leave from. Null
     * when the product has never been costed -- the report then says the margin is unknown
     * rather than calling the goods free.
     */
    public static BigDecimal costOfSale(Product product) {
        return product.getAverageCost();
    }

    /**
     * Margin as a percentage of the selling price, one decimal -- the "taux de marque", which is
     * the figure a shopkeeper means by "I make 20% on rice". Null when nothing was sold, since
     * a rate of zero would claim the goods sold at cost.
     */
    public static BigDecimal marginRate(BigDecimal margin, BigDecimal revenue) {
        if (margin == null || revenue == null || revenue.signum() == 0) {
            return null;
        }
        return margin.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP);
    }

    /** A cost as stored: two places, half up. Null stays null. */
    public static BigDecimal scaled(BigDecimal cost) {
        return cost == null ? null : cost.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }
}
