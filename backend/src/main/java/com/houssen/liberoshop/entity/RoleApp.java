package com.houssen.liberoshop.entity;

public enum RoleApp {
    CASHIER,
    /** Takes orders -- receptionist, waiter -- from a phone; never takes the money. */
    ORDER_TAKER,
    DEPOT_AGENT,
    DEPOT_MANAGER,
    SUPER_ADMIN
}
