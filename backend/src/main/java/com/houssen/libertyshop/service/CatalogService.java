package com.houssen.libertyshop.service;

import com.houssen.libertyshop.entity.Product;
import com.houssen.libertyshop.repository.CategoryRepository;
import com.houssen.libertyshop.repository.ProductRepository;
import com.houssen.libertyshop.repository.StockMovementRepository;
import com.houssen.libertyshop.repository.SupplierRepository;
import com.houssen.libertyshop.web.dto.CategoryResponse;
import com.houssen.libertyshop.web.dto.CategoryStockResponse;
import com.houssen.libertyshop.web.dto.ProductResponse;
import com.houssen.libertyshop.web.dto.SupplierResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public CatalogService(ProductRepository products, CategoryRepository categories,
                          SupplierRepository suppliers, StockMovementRepository movements) {
        this.products = products;
        this.categories = categories;
        this.suppliers = suppliers;
        this.movements = movements;
    }

    /**
     * @param search      matched against name, barcode and category name; null or blank means all
     * @param categoryId  restrict to one category, or null
     * @param lowStockOnly keep only the references under the alert threshold
     */
    public List<ProductResponse> findProducts(String search, Long categoryId, boolean lowStockOnly) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return products.findAllWithCategory().stream()
                .filter(product -> categoryId == null
                        || (product.getCategory() != null && categoryId.equals(product.getCategory().getId())))
                .filter(product -> !lowStockOnly || StockPolicy.isLowStock(product))
                .filter(product -> needle.isEmpty() || matches(product, needle))
                .map(ProductResponse::of)
                .toList();
    }

    public List<ProductResponse> findLowStock() {
        return products.findLowStock(StockPolicy.LOW_STOCK_THRESHOLD).stream()
                .map(ProductResponse::of)
                .toList();
    }

    public List<CategoryResponse> findCategories() {
        return categories.findAllByOrderByNameAsc().stream().map(CategoryResponse::of).toList();
    }

    /** Suppliers, each carrying how much has actually been received from them. */
    public List<SupplierResponse> findSuppliers() {
        Map<Long, long[]> stats = new HashMap<>();
        for (Object[] row : movements.aggregateSuppliesBySupplier()) {
            stats.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return suppliers.findAllByOrderByNameAsc().stream()
                .map(supplier -> {
                    long[] counters = stats.getOrDefault(supplier.getId(), new long[]{0L, 0L});
                    return SupplierResponse.of(supplier, counters[0], counters[1]);
                })
                .toList();
    }

    /** Stock value split by category, for the super-admin stock screen. */
    public List<CategoryStockResponse> stockByCategory() {
        List<Product> all = products.findAllWithCategory();
        BigDecimal total = all.stream()
                .map(CatalogService::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return categories.findAllByOrderByNameAsc().stream()
                .map(category -> {
                    List<Product> inCategory = all.stream()
                            .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(category.getId()))
                            .toList();
                    BigDecimal value = inCategory.stream()
                            .map(CatalogService::valueOf)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new CategoryStockResponse(
                            CategoryResponse.of(category),
                            inCategory.size(),
                            inCategory.stream().mapToLong(Product::getStockQuantity).sum(),
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

    public long totalUnitsInStock() {
        return products.findAll().stream().mapToLong(Product::getStockQuantity).sum();
    }

    public long countProducts() {
        return products.count();
    }

    public long countLowStock() {
        return products.findLowStock(StockPolicy.LOW_STOCK_THRESHOLD).size();
    }

    static BigDecimal valueOf(Product product) {
        return product.getPrice().multiply(BigDecimal.valueOf(product.getStockQuantity()));
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
