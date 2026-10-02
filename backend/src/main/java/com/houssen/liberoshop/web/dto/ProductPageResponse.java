package com.houssen.liberoshop.web.dto;

import java.util.List;

/**
 * One page of a product browse: the products, and enough to offer the next page.
 *
 * @param total   how many products match in all, so the grid can say "40 sur 130"
 * @param hasMore whether a next page exists -- said by the server, so the screen does not redo
 *                the arithmetic
 */
public record ProductPageResponse(List<ProductResponse> items, long total, int page, int size,
                                  boolean hasMore) {
}
