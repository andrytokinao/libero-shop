package com.houssen.liberoshop.web.dto;

import java.util.List;

/**
 * What one seller pinned, newest first -- by id only: the screens already hold the products and
 * the rayon tree, and only need to know which tiles and chips wear the star.
 */
public record FavoritesResponse(List<Long> productIds, List<Long> categoryIds) {
}
