package com.houssen.liberoshop.entity;

public enum DeliveryStatus {
    /** Waiting at the depot (kitchen, bar) for someone to prepare and hand it over. */
    PENDING,
    /**
     * Someone has taken it on -- "je m'en occupe" -- and is preparing it: nobody else hands it
     * over, so two storekeepers never pick the same order. Who and since when are on the invoice.
     */
    IN_PROGRESS,
    DELIVERED,
    /** Cancelled before hand-over: the goods went back on the shelf, nothing is left to hand over. */
    CANCELLED
}
