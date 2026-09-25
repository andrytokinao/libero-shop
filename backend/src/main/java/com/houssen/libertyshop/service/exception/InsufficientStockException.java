package com.houssen.libertyshop.service.exception;

/** Not enough units left to satisfy a sale line. */
public class InsufficientStockException extends BusinessRuleException {

    public InsufficientStockException(String productName, int requested, int available) {
        super("INSUFFICIENT_STOCK",
                "Stock insuffisant pour " + productName + " : " + requested
                        + " demande(s), " + available + " disponible(s).");
    }
}
