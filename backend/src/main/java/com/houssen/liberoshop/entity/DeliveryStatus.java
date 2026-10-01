package com.houssen.liberoshop.entity;

public enum DeliveryStatus {
    PENDING,
    DELIVERED,
    /** Cancelled before hand-over: the goods went back on the shelf, nothing is left to hand over. */
    CANCELLED
}
