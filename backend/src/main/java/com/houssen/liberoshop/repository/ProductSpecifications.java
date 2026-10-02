package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.util.SearchText;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The criteria a product search is assembled from, one per concern, combined by the caller.
 *
 * <p>Specifications rather than one query per combination of filters: the till searches by
 * words, the order screen by words within a rayon, the favourites by what a seller pinned, and
 * the next filter -- in stock only, a supplier -- is one more method here rather than a new query
 * beside each of the others.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /**
     * Every word appears in the product's name or barcode, or in the name of its rayon or of any
     * rayon above it -- in any order, so "1 5 coca" finds "Coca-Cola 1,5 L", and "boissons"
     * finds the waters filed under "Boissons &gt; Eau", not only what sits on "Boissons" itself.
     *
     * @param words already folded by {@link SearchText#words}, hence plain {@code [a-z0-9]}:
     *              nothing in them can act as a LIKE wildcard, so none is escaped
     */
    public static Specification<Product> containsAllWords(List<String> words) {
        return Specification.allOf(words.stream().map(ProductSpecifications::containsWord).toList());
    }

    /** Filed in one of these rayons; no restriction at all for an empty collection. */
    public static Specification<Product> inCategories(Collection<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> root.get("category").get("id").in(categoryIds);
    }

    /**
     * Filed directly in this rayon, not below it -- what a folder holds besides its sub-folders,
     * the way a file explorer lists a directory.
     */
    public static Specification<Product> directlyIn(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    /**
     * One of these products, or filed in one of these rayons -- what a seller pinned: articles
     * one by one, and whole rayons. Matches nothing when both are empty, unlike
     * {@link #inCategories}: no favourites is an empty list, not the whole shop.
     */
    public static Specification<Product> amongOrIn(Collection<Long> productIds, Collection<Long> categoryIds) {
        return (root, query, cb) -> {
            List<Predicate> any = new ArrayList<>();
            if (!productIds.isEmpty()) {
                any.add(root.get("id").in(productIds));
            }
            if (!categoryIds.isEmpty()) {
                any.add(root.get("category").get("id").in(categoryIds));
            }
            return any.isEmpty() ? cb.disjunction() : cb.or(any.toArray(Predicate[]::new));
        };
    }

    private static Specification<Product> containsWord(String word) {
        return (root, query, cb) -> {
            String pattern = "%" + word + "%";
            List<Predicate> any = new ArrayList<>();
            any.add(cb.like(root.get("searchText"), pattern));
            for (Join<?, Category> rayon : rayonChain(root)) {
                any.add(cb.like(rayon.get("searchText"), pattern));
            }
            return cb.or(any.toArray(Predicate[]::new));
        };
    }

    /**
     * The product's rayon and its ancestors, as outer joins: the rayon, its parent, and so on up
     * to {@link Category#MAX_DEPTH} levels. Created once per query and shared by every word,
     * rather than one chain of joins each.
     */
    private static List<Join<?, Category>> rayonChain(Root<Product> root) {
        List<Join<?, Category>> chain = new ArrayList<>();
        Join<?, Category> level = joinOf(root, "category");
        chain.add(level);
        for (int depth = 1; depth < Category.MAX_DEPTH; depth++) {
            level = joinOf(level, "parent");
            chain.add(level);
        }
        return chain;
    }

    /** The existing outer join of that attribute on {@code from}, or a new one. */
    @SuppressWarnings("unchecked")
    private static Join<?, Category> joinOf(From<?, ?> from, String attribute) {
        return from.getJoins().stream()
                .filter(join -> join.getAttribute().getName().equals(attribute))
                .map(join -> (Join<?, Category>) join)
                .findFirst()
                .orElseGet(() -> from.join(attribute, JoinType.LEFT));
    }
}
