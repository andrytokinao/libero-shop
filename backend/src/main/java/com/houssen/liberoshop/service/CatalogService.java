package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.util.Quantities;
import com.houssen.liberoshop.web.dto.CategoryResponse;
import com.houssen.liberoshop.web.dto.CategoryStockResponse;
import com.houssen.liberoshop.web.dto.ProductResponse;
import com.houssen.liberoshop.web.dto.SupplierResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read side of the catalogue: products, categories, suppliers.
 *
 * <p>No {@code @RequiresActiveLicense} anywhere here on purpose. An expired license makes
 * the application read-only, and consulting stock and prices must keep working -- the shop
 * still has to answer a customer standing at the counter.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;
    private final StockMovementRepository movements;
    private final CategoryService categoryService;

    public CatalogService(ProductRepository products, CategoryRepository categories,
                          SupplierRepository suppliers, StockMovementRepository movements,
                          CategoryService categoryService) {
        this.products = products;
        this.categories = categories;
        this.suppliers = suppliers;
        this.movements = movements;
        this.categoryService = categoryService;
    }

    /**
     * @param search      matched against name, barcode and category name; null or blank means all
     * @param categoryId  restrict to one rayon <em>and everything below it</em>, or null for all
     *                    -- see {@link CategoryService#branchOf(Long)} for why the children count
     * @param lowStockOnly keep only the references under the alert threshold
     */
    public List<ProductResponse> findProducts(String search, Long categoryId, boolean lowStockOnly) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Set<Long> branch = categoryService.branchOf(categoryId);
        return products.findAllWithCategory().stream()
                .filter(product -> categoryId == null
                        || (product.getCategory() != null
                        && branch.contains(product.getCategory().getId())))
                .filter(product -> !lowStockOnly || StockPolicy.isLowStock(product))
                .filter(product -> needle.isEmpty() || matches(product, needle))
                .map(ProductResponse::of)
                .toList();
    }

    /**
     * These products as the lists show them, read in a transaction of their own: for the listeners
     * that run after a commit, once the transaction that moved their stock is over.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<ProductResponse> findAllById(Collection<Long> ids) {
        return products.findAllWithCategoryByIdIn(ids).stream()
                .map(ProductResponse::of)
                .toList();
    }

    public List<ProductResponse> findLowStock() {
        return products.findLowStock(StockPolicy.LOW_STOCK_THRESHOLD).stream()
                .map(ProductResponse::of)
                .toList();
    }

    /** Suppliers, each carrying how much has actually been received from them. */
    public List<SupplierResponse> findSuppliers() {
        Map<Long, Received> stats = new HashMap<>();
        for (Object[] row : movements.aggregateSuppliesBySupplier()) {
            stats.put((Long) row[0], new Received(((Number) row[1]).longValue(), Quantities.orZero(row[2])));
        }
        return suppliers.findAllByOrderByNameAsc().stream()
                .map(supplier -> {
                    Received received = stats.getOrDefault(supplier.getId(), Received.NOTHING);
                    return SupplierResponse.of(supplier, received.deliveries(), received.units());
                })
                .toList();
    }

    /** What one supplier has delivered: how many times, and how much in base units. */
    private record Received(long deliveries, BigDecimal units) {
        static final Received NOTHING = new Received(0, Quantities.of(0));
    }

    /** Stock value split by category, for the super-admin stock screen. */
    public List<CategoryStockResponse> stockByCategory() {
        List<Product> all = products.findAllWithCategory();
        BigDecimal total = all.stream()
                .map(CatalogService::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return categories.findAllWithParent().stream()
                .map(category -> {
                    List<Product> inCategory = all.stream()
                            .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(category.getId()))
                            .toList();
                    BigDecimal value = inCategory.stream()
                            .map(CatalogService::valueOf)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new CategoryStockResponse(
                            CategoryResponse.of(category),
                            CategoryService.pathOf(category),
                            inCategory.size(),
                            inCategory.stream().map(Product::getStockQuantity).reduce(BigDecimal.ZERO, BigDecimal::add),
                            value,
                            percentOf(value, total));
                })
                .sorted((a, b) -> b.value().compareTo(a.value()))
                .toList();
    }

    public BigDecimal totalStockValue() {
        return products.findAll().stream()
                .map(CatalogService::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalUnitsInStock() {
        return products.findAll().stream().map(Product::getStockQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public long countProducts() {
        return products.count();
    }

    public long countLowStock() {
        return products.findLowStock(StockPolicy.LOW_STOCK_THRESHOLD).size();
    }

    static BigDecimal valueOf(Product product) {
        return product.getPrice().multiply(product.getStockQuantity()).setScale(2, RoundingMode.HALF_UP);
    }

    private static int percentOf(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return 0;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private static boolean matches(Product product, String needle) {
        return contains(product.getName(), needle)
                || contains(product.getBarcode(), needle)
                || (product.getCategory() != null && contains(product.getCategory().getName(), needle));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
