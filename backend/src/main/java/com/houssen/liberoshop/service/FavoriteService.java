package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Favorite;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.FavoriteRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.ProductSpecifications;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.FavoritesResponse;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The products and rayons a seller pinned -- see {@link com.houssen.liberoshop.entity.Favorite}.
 *
 * <p>Pinning is idempotent both ways: pinning twice keeps one pin, unpinning what is not pinned
 * is not an error. A double tap on a star must never end in a refusal.
 *
 * <p>No {@code @RequiresActiveLicense}, like a profile photo: a seller's own shortcuts are not
 * the shop's business data, and an expired licence leaves the catalogue readable.
 */
@Service
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favorites;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CategoryService categoryService;
    private final BusinessCalendar calendar;

    public FavoriteService(FavoriteRepository favorites, ProductRepository products,
                           CategoryRepository categories, CategoryService categoryService,
                           BusinessCalendar calendar) {
        this.favorites = favorites;
        this.products = products;
        this.categories = categories;
        this.categoryService = categoryService;
        this.calendar = calendar;
    }

    public FavoritesResponse of(UserApp user) {
        return new FavoritesResponse(favorites.findPinnedProductIds(user.getId()),
                favorites.findPinnedCategoryIds(user.getId()));
    }

    /**
     * The products a seller reaches through their favourites: the ones pinned, and everything
     * filed in a pinned rayon or below it -- pinning "Boissons" brings the waters too, as
     * opening "Boissons" does.
     */
    public Specification<Product> pinnedBy(UserApp user) {
        Set<Long> rayons = new LinkedHashSet<>();
        favorites.findPinnedCategoryIds(user.getId()).forEach(id -> rayons.addAll(categoryService.branchOf(id)));
        return ProductSpecifications.amongOrIn(favorites.findPinnedProductIds(user.getId()), rayons);
    }

    @Transactional
    public FavoritesResponse pinProduct(UserApp user, Long productId) {
        if (favorites.findByUserIdAndProductId(user.getId(), productId).isEmpty()) {
            favorites.save(Favorite.builder()
                    .user(user)
                    .product(products.findById(productId)
                            .orElseThrow(() -> ResourceNotFoundException.of("Produit", productId)))
                    .pinnedAt(calendar.now())
                    .build());
        }
        return of(user);
    }

    @Transactional
    public FavoritesResponse unpinProduct(UserApp user, Long productId) {
        favorites.findByUserIdAndProductId(user.getId(), productId).ifPresent(favorites::delete);
        return of(user);
    }

    @Transactional
    public FavoritesResponse pinCategory(UserApp user, Long categoryId) {
        if (favorites.findByUserIdAndCategoryId(user.getId(), categoryId).isEmpty()) {
            favorites.save(Favorite.builder()
                    .user(user)
                    .category(categories.findById(categoryId)
                            .orElseThrow(() -> ResourceNotFoundException.of("Categorie", categoryId)))
                    .pinnedAt(calendar.now())
                    .build());
        }
        return of(user);
    }

    @Transactional
    public FavoritesResponse unpinCategory(UserApp user, Long categoryId) {
        favorites.findByUserIdAndCategoryId(user.getId(), categoryId).ifPresent(favorites::delete);
        return of(user);
    }
}
