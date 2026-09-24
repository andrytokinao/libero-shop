package com.houssen.libertyshop.entity;

/**
 * Small supporting enums, grouped in one file for convenience.
 * Package-private: only used inside com.houssen entities.
 */
enum PaymentStatus {
    PAID,
    UNPAID
}

enum DeliveryStatus {
    PENDING,
    DELIVERED
}

enum RemittanceStatus {
    PENDING,
    CONFIRMED
}

enum PaymentMethod {
    CASH,
    MOBILE_MONEY,
    BANK_TRANSFER,
    OTHER
}
