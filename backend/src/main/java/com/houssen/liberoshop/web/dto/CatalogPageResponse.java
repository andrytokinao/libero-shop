package com.houssen.liberoshop.web.dto;

import java.util.List;

/**
 * One page of the catalogue explorer.
 *
 * <p>The folders come first, on the first page only -- a rayon holds a handful of sub-rayons and
 * they belong at the top, as in a file explorer -- then a page of products.
 *
 * @param totalProducts how many products the open folder (or search) holds in all, for "Afficher plus"
 * @param hasMore       whether a further page of products exists
 */
public record CatalogPageResponse(List<CatalogEntry> entries, long totalProducts, int page, boolean hasMore) {
}
