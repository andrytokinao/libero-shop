package com.houssen.liberoshop.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One page of the stock outputs, with what the whole answer adds up to.
 *
 * @param total         how many outputs match in all, so the screen can say "50 sur 1 240"
 * @param totalQuantity units that left across every matching output, not only this page's
 * @param totalValue    their value at each product's current price, likewise
 * @param hasMore       whether a next page exists -- said by the server, so the screen does not
 *                      redo the arithmetic
 */
public record StockOutputPageResponse(List<StockOutputResponse> items, long total,
                                      BigDecimal totalQuantity, BigDecimal totalValue,
                                      int page, int size, boolean hasMore) {
}
