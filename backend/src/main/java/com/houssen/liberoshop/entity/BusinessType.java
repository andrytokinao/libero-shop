package com.houssen.liberoshop.entity;

/**
 * What kind of business the installation runs. Picking one pre-fills the settings that suit it;
 * each of them can then be changed on its own, so a type is a starting point, never a lock.
 *
 * <p>The type also names things on screen -- "Cuisine" rather than "Dépôt", "Table" rather than
 * "Client" -- which the front end derives from it.
 */
public enum BusinessType {

    /** One counter: what is sold leaves with the customer, paid there and then. */
    COUNTER(new ShopFeatures(false, false, false, false, false, false)),
    /** A shop with a store-room: the goods are handed over at the depot, which may take the money. */
    GROCERY_WITH_DEPOT(new ShopFeatures(true, true, false, false, false, false)),
    /** Several people, so the depot's cash is counted by a second one before it is paid. */
    WHOLESALE_DEPOT(new ShopFeatures(true, true, true, false, false, false)),
    /** The kitchen serves, the bill is settled at the till. */
    RESTAURANT(new ShopFeatures(true, false, false, false, false, false)),
    BAR(new ShopFeatures(true, false, false, false, false, false)),
    /** The reception takes the guest's money and brings it to the till. */
    HOTEL(new ShopFeatures(true, false, false, false, true, false));

    private final ShopFeatures defaults;

    BusinessType(ShopFeatures defaults) {
        this.defaults = defaults;
    }

    public ShopFeatures defaults() {
        return defaults;
    }
}
