package com.houssen.liberoshop.web;

import com.houssen.liberoshop.security.CurrentUser;
import com.houssen.liberoshop.service.FavoriteService;
import com.houssen.liberoshop.web.dto.FavoritesResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in seller's own favourites. Open to every role: whoever sells -- cashier, order
 * taker, depot -- pins what they reach for. Always the caller's own, read from the session: there
 * is no way to address another account's pins.
 *
 * <p>PUT to pin and DELETE to unpin, both idempotent, both answering the whole list so the
 * screen re-renders from what the server holds.
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final CurrentUser currentUser;

    public FavoriteController(FavoriteService favoriteService, CurrentUser currentUser) {
        this.favoriteService = favoriteService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public FavoritesResponse mine() {
        return favoriteService.of(currentUser.require());
    }

    @PutMapping("/products/{productId}")
    public FavoritesResponse pinProduct(@PathVariable Long productId) {
        return favoriteService.pinProduct(currentUser.require(), productId);
    }

    @DeleteMapping("/products/{productId}")
    public FavoritesResponse unpinProduct(@PathVariable Long productId) {
        return favoriteService.unpinProduct(currentUser.require(), productId);
    }

    @PutMapping("/categories/{categoryId}")
    public FavoritesResponse pinCategory(@PathVariable Long categoryId) {
        return favoriteService.pinCategory(currentUser.require(), categoryId);
    }

    @DeleteMapping("/categories/{categoryId}")
    public FavoritesResponse unpinCategory(@PathVariable Long categoryId) {
        return favoriteService.unpinCategory(currentUser.require(), categoryId);
    }
}
