package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SaleLineRepository;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.util.SearchText;
import com.houssen.liberoshop.web.dto.ProductResponse;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.houssen.liberoshop.repository.ProductSpecifications.containsAllWords;
import static com.houssen.liberoshop.repository.ProductSpecifications.inCategories;

/**
 * How the screens that sell find a product among thousands: by its code, by a few words, or
 * -- before anything is typed -- among the ones that sell the most.
 *
 * <p>Every answer is bounded. A sale screen shows a grid a person reads at a glance, so it asks
 * for a few dozen products and gets them filtered, sorted and cut by the database; the whole
 * catalogue never travels for a sale. {@link CatalogService#findProducts} stays the
 * unbounded read for the stock screens, which do need every row.
 *
 * <p>Read-only and without {@code @RequiresActiveLicense}, like the rest of the catalogue: an
 * expired license must still let the shop answer a customer's question.
 */
@Service
@Transactional(readOnly = true)
public class ProductSearchService {

    public static final int DEFAULT_LIMIT = 40;
    /** A grid larger than this is no longer read, only scrolled. */
    public static final int MAX_LIMIT = 100;
    /** How far back "sells the most" looks: long enough to be stable, short enough to follow the season. */
    static final int BEST_SELLERS_WINDOW_DAYS = 30;

    private static final Sort BY_NAME = Sort.by("name");

    private final ProductRepository products;
    private final SaleLineRepository saleLines;
    private final CategoryService categories;
    private final BusinessCalendar calendar;

    public ProductSearchService(ProductRepository products, SaleLineRepository saleLines,
                                CategoryService categories, BusinessCalendar calendar) {
        this.products = products;
        this.saleLines = saleLines;
        this.categories = categories;
        this.calendar = calendar;
    }

    /**
     * The one product a code designates -- what a barcode scanner reads, or the short code a
     * shop gives its loose goods ("12" for the baguette), both kept in the barcode column.
     *
     * <p>Matched whole, unlike {@link #search}: a scan is an identity, not a search, so "12"
     * must not land on "6111234567812" because it happens to end with it.
     *
     * @throws ResourceNotFoundException when no product carries that code -- an ordinary outcome
     *                                   at the till, which the caller is expected to handle
     */
    public ProductResponse findByCode(String code) {
        String key = code == null ? "" : code.trim();
        return products.findWithCategoryByBarcode(key)
                .map(ProductResponse::of)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun produit ne porte le code « " + key + " »."));
    }

    /**
     * Products whose name, barcode or rayon contain every word of the query, accents and case
     * ignored, by name.
     *
     * @param query      a few words; blank means no word filter
     * @param categoryId a rayon and everything below it, or null for the whole shop
     * @param limit      clamped to {@code 1..MAX_LIMIT}
     */
    public List<ProductResponse> search(String query, Long categoryId, int limit) {
        Specification<Product> criteria = containsAllWords(SearchText.words(query))
                .and(inCategories(categories.branchOf(categoryId)));
        return find(criteria, clamp(limit)).stream().map(ProductResponse::of).toList();
    }

    /**
     * What a sale screen shows before anything is typed: the best sellers of the last
     * {@value #BEST_SELLERS_WINDOW_DAYS} days, then -- for a shop too new to have that many --
     * the rest of the catalogue by name, so the grid is never empty while products exist.
     */
    public List<ProductResponse> featured(int limit) {
        int size = clamp(limit);
        List<Long> bestIds = saleLines.findBestSellingProductIds(
                calendar.startOfDaysAgo(BEST_SELLERS_WINDOW_DAYS), Limit.of(size));

        Map<Long, Product> byId = products.findAllWithCategoryByIdIn(bestIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Product> featured = new ArrayList<>(
                bestIds.stream().map(byId::get).filter(Objects::nonNull).toList());

        if (featured.size() < size) {
            // Asking for `size` is enough: at most featured.size() of them are repeats.
            find(Specification.unrestricted(), size).stream()
                    .filter(product -> !byId.containsKey(product.getId()))
                    .limit(size - featured.size())
                    .forEach(featured::add);
        }
        return featured.stream().map(ProductResponse::of).toList();
    }

    private List<Product> find(Specification<Product> criteria, int limit) {
        return products.findBy(criteria, query -> query
                .sortBy(BY_NAME)
                .limit(limit)
                .project("category")
                .all());
    }

    private static int clamp(int limit) {
        return Math.clamp(limit, 1, MAX_LIMIT);
    }
}
