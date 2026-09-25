package com.houssen.libertyshop.entity;

/**
 * How a payment was received. Persisted by name, so new means of payment are added as
 * new constants without touching the existing rows.
 */
public enum PaymentMethod {
    CASH,
    MOBILE_MONEY,
    BANK_TRANSFER,
    OTHER
}
