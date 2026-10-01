package com.houssen.liberoshop.entity;

/**
 * The switches that bend how an order goes from the till to the customer.
 *
 * @param separateDelivery      orders wait to be handed over (depot, kitchen, bar); off, a sale
 *                              is handed over the moment it is recorded
 * @param payAtDepot            whoever hands an unpaid order over takes the money and later brings
 *                              it to the till; off, an unpaid order is settled at the till only
 * @param dualControlRemittance the cash brought to the till -- from the depot or from an order
 *                              taker -- must be confirmed by someone other than who brought it
 * @param cancelAfterDelivery   an unpaid order already handed over may still be cancelled -- the
 *                              customer left without paying -- to record what happened; the goods
 *                              are gone, so nothing returns to stock
 * @param orderTakerCollects    whoever takes orders (receptionist, waiter) may also take the
 *                              customer's cash, and brings it to the till like the depot does
 * @param onlineOrdering        customers order from their phone by scanning their table's QR code
 */
public record ShopFeatures(boolean separateDelivery,
                           boolean payAtDepot,
                           boolean dualControlRemittance,
                           boolean cancelAfterDelivery,
                           boolean orderTakerCollects,
                           boolean onlineOrdering) {

    /**
     * Drops the switches that mean nothing without the one they depend on: with no separate
     * hand-over there is no depot to pay at, and with no cash brought to the till by anyone there
     * is nothing to confirm.
     */
    public ShopFeatures normalized() {
        boolean depotCash = separateDelivery && payAtDepot;
        boolean cashTrail = depotCash || orderTakerCollects;
        return new ShopFeatures(separateDelivery, depotCash, cashTrail && dualControlRemittance,
                cancelAfterDelivery, orderTakerCollects, onlineOrdering);
    }
}
