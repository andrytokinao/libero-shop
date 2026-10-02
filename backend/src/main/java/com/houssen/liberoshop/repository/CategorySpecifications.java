package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.util.SearchText;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/** The criteria a rayon search is assembled from -- the counterpart of {@link ProductSpecifications}. */
public final class CategorySpecifications {

    private CategorySpecifications() {
    }

    /**
     * Every word appears in the rayon's own name, accents and case ignored: "eau" finds "Eau"
     * and "Eau gazeuse", not "Boissons" -- the rayon a seller is looking for, not every one above it.
     *
     * @param words already folded by {@link SearchText#words}; nothing in them is a LIKE wildcard
     */
    public static Specification<Category> nameContainsAllWords(List<String> words) {
        return Specification.allOf(words.stream()
                .map(word -> (Specification<Category>) (root, query, cb) ->
                        cb.like(root.get("searchText"), "%" + word + "%"))
                .toList());
    }
}
