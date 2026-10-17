package com.houssen.liberoshop.web.dto;

/**
 * How far a new shop has got in setting itself up: each step ticks itself once it is done.
 *
 * @param product       a first product exists
 * @param purchasePrice a purchase price is known for at least one product
 * @param barcode       a barcode is attached to a product or to one of its units
 * @param stock         some goods are in stock
 * @param cashier       an enabled account can sell at the till
 * @param sale          a first sale was made
 */
public record FirstStepsResponse(boolean product, boolean purchasePrice, boolean barcode,
                                 boolean stock, boolean cashier, boolean sale) {
}
