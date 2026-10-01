package com.houssen.liberoshop.entity;

/** Why an order was cancelled, picked from a short list so the reports can count them. */
public enum CancelReason {
    /** Wrong article or quantity typed: the order should never have existed. */
    INPUT_ERROR,
    /** The customer changed their mind, or left. */
    CUSTOMER_GAVE_UP,
    /** Anything else; the comment then says what. */
    OTHER
}
