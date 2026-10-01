package com.houssen.liberoshop.bootstrap;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fills {@code searchText} on the products and rayons of an installation created before it
 * existed.
 *
 * <p>{@code ddl-auto=update} adds the column empty, and the entities only fill it when they are
 * next written; until then the sale screens' search would not see them. The folding is done in
 * Java -- it is {@code SearchText}'s, and no SQL dialect folds accents the same way -- so this
 * goes through the entities rather than one {@code UPDATE}.
 *
 * <p>Idempotent and cheap once done: on every later start both queries return nothing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class SearchTextBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchTextBackfill.class);

    private final ProductRepository products;
    private final CategoryRepository categories;

    public SearchTextBackfill(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Product> staleProducts = products.findBySearchTextIsNull();
        staleProducts.forEach(Product::refreshSearchText);
        List<Category> staleCategories = categories.findBySearchTextIsNull();
        staleCategories.forEach(Category::refreshSearchText);

        if (!staleProducts.isEmpty() || !staleCategories.isEmpty()) {
            log.info("Texte de recherche calcule pour {} produit(s) et {} categorie(s).",
                    staleProducts.size(), staleCategories.size());
        }
    }
}
