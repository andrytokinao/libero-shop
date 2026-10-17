package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Filing products in a rayon: one product changing rayon, a product added to the rayon on screen,
 * or every product of a rayon moved to another one.
 *
 * <p>Only the rayon changes -- name, price, stock and history stay. The tills hear of it, since
 * their folders list products by rayon.
 */
@Service
@Transactional(readOnly = true)
public class ProductFilingService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ApplicationEventPublisher events;

    public ProductFilingService(ProductRepository products, CategoryRepository categories,
                                ApplicationEventPublisher events) {
        this.products = products;
        this.categories = categories;
        this.events = events;
    }

    /**
     * @return how many products changed rayon -- those already in it are left as they are
     * @throws ResourceNotFoundException for an unknown rayon or product, before anything is moved
     */
    @RequiresActiveLicense
    @Transactional
    public int file(Collection<Long> productIds, Long categoryId) {
        Category target = categories.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Categorie", categoryId));
        Set<Long> wanted = new LinkedHashSet<>(productIds);
        if (wanted.isEmpty()) {
            throw new BusinessRuleException("FILING_NOTHING", "Aucun produit a ranger.");
        }
        List<Product> found = products.findAllWithCategoryByIdIn(wanted);
        if (found.size() != wanted.size()) {
            Long missing = wanted.stream()
                    .filter(id -> found.stream().noneMatch(product -> product.getId().equals(id)))
                    .findFirst().orElseThrow();
            throw ResourceNotFoundException.of("Produit", missing);
        }
        List<Product> moved = found.stream()
                .filter(product -> product.getCategory() == null || !product.getCategory().getId().equals(categoryId))
                .toList();
        moved.forEach(product -> product.setCategory(target));
        if (!moved.isEmpty()) {
            events.publishEvent(new StockChangedEvent(moved.stream().map(Product::getId).toList()));
        }
        return moved.size();
    }

    /** Every product filed directly in {@code fromId} moved to {@code toId}; the rayon itself stays. */
    @RequiresActiveLicense
    @Transactional
    public int moveAll(Long fromId, Long toId) {
        if (fromId.equals(toId)) {
            throw new BusinessRuleException("CATEGORY_SAME_TARGET", "Choisissez un autre rayon que celui-ci.");
        }
        List<Long> held = products.findAllWithCategory().stream()
                .filter(product -> product.getCategory() != null && product.getCategory().getId().equals(fromId))
                .map(Product::getId)
                .toList();
        if (held.isEmpty()) {
            return 0;
        }
        return file(held, toId);
    }
}
