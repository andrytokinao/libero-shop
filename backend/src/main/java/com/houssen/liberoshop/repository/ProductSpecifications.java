package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.util.SearchText;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;

/**
 * The criteria a product search is assembled from, one per concern, combined by the caller.
 *
 * <p>Specifications rather than one query per combination of filters: the till searches by
 * words, the order screen by words within a rayon, and the next filter -- in stock only, a
 * supplier -- is one more method here rather than a new query beside each of the others.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /**
     * Every word appears in the product's name or barcode, or in its rayon's name -- in any
     * order, so "1 5 coca" finds "Coca-Cola 1,5 L".
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

    private static Specification<Product> containsWord(String word) {
        return (root, query, cb) -> {
            String pattern = "%" + word + "%";
            return cb.or(
                    cb.like(root.get("searchText"), pattern),
                    cb.like(categoryOf(root).get("searchText"), pattern));
        };
    }

    /** One outer join on the rayon, shared by every word rather than one join each. */
    @SuppressWarnings("unchecked")
    private static Join<Product, Category> categoryOf(Root<Product> root) {
        return root.getJoins().stream()
                .filter(join -> join.getAttribute().getName().equals("category"))
                .map(join -> (Join<Product, Category>) join)
                .findFirst()
                .orElseGet(() -> root.join("category", JoinType.LEFT));
    }
}
