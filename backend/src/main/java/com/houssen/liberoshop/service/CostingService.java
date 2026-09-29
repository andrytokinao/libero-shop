package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SaleLineRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.MarginReportResponse;
import com.houssen.liberoshop.web.dto.MarginReportResponse.ProductMargin;
import com.houssen.liberoshop.web.dto.ProductCostResponse;
import com.houssen.liberoshop.web.dto.SupplierPriceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side of purchase costs: what each product costs, what each supplier charges for it, and
 * what the shop earned over a period.
 *
 * <p>Nothing is computed from the purchase history at report time. The average cost is kept up
 * to date on the product by {@link PurchaseCosting} as goods come in, and frozen on each sale
 * line as they go out, so a margin report is one grouped query over the sale lines however long
 * the shop has been trading. The receipts are read only to answer questions about suppliers.
 *
 * <p>No {@code @RequiresActiveLicense}: like the catalogue, these are reads, and an expired
 * license leaves the shop read-only rather than blind.
 */
@Service
@Transactional(readOnly = true)
public class CostingService {

    /** Wide enough for a year-on-year comparison, narrow enough to stay a single report. */
    static final int MAX_REPORT_DAYS = 366;

    /** Label of the receipts written by an import without a supplier. */
    static final String NO_SUPPLIER_LABEL = "Import / inventaire";

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final SaleLineRepository saleLines;
    private final BusinessCalendar calendar;

    public CostingService(ProductRepository products, StockMovementRepository movements,
                          SaleLineRepository saleLines, BusinessCalendar calendar) {
        this.products = products;
        this.movements = movements;
        this.saleLines = saleLines;
        this.calendar = calendar;
    }

    // ------------------------------------------------------------------ products

    /** Every product with its average cost, its unit margin and the last price paid for it. */
    public List<ProductCostResponse> findProductCosts() {
        Map<Long, Supply> latest = movements.findLatestCostedSupplyPerProduct().stream()
                .collect(Collectors.toMap(supply -> supply.getProduct().getId(),
                        Function.identity(), (a, b) -> a));

        return products.findAllWithCategory().stream()
                .map(product -> costOf(product, latest.get(product.getId())))
                .toList();
    }

    private static ProductCostResponse costOf(Product product, Supply latest) {
        BigDecimal average = product.getAverageCost();
        BigDecimal unitMargin = average == null ? null : product.getPrice().subtract(average);
        return new ProductCostResponse(
                product.getId(),
                product.getName(),
                product.getUnit(),
                product.getPrice(),
                product.getStockQuantity(),
                average,
                unitMargin,
                PurchaseCosting.marginRate(unitMargin, product.getPrice()),
                latest == null ? null : latest.getUnitCost(),
                latest == null || latest.getSupplier() == null ? null : latest.getSupplier().getName(),
                latest == null ? null : latest.getMovementDate());
    }

    // ----------------------------------------------------------------- suppliers

    /**
     * What each supplier has charged for one product, cheapest on average first -- the
     * comparison to read before placing the next order.
     */
    public List<SupplierPriceResponse> supplierPricesOf(Long productId) {
        if (!products.existsById(productId)) {
            throw ResourceNotFoundException.of("Produit", productId);
        }
        // Newest first, and LinkedHashMap keeps it so: the first receipt a group meets is its last.
        Map<Long, List<Supply>> bySupplier = new LinkedHashMap<>();
        for (Supply supply : movements.findCostedSuppliesOf(productId)) {
            Long key = supply.getSupplier() == null ? null : supply.getSupplier().getId();
            bySupplier.computeIfAbsent(key, k -> new ArrayList<>()).add(supply);
        }
        return bySupplier.entrySet().stream()
                .map(entry -> priceOf(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(SupplierPriceResponse::averageCost))
                .toList();
    }

    private static SupplierPriceResponse priceOf(Long supplierId, List<Supply> receipts) {
        Supply last = receipts.getFirst();
        long units = receipts.stream().mapToLong(Supply::getQuantity).sum();
        BigDecimal spent = receipts.stream()
                .map(s -> s.getUnitCost().multiply(BigDecimal.valueOf(s.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SupplierPriceResponse(
                supplierId,
                last.getSupplier() == null ? NO_SUPPLIER_LABEL : last.getSupplier().getName(),
                receipts.size(),
                units,
                units == 0 ? last.getUnitCost()
                        : spent.divide(BigDecimal.valueOf(units), PurchaseCosting.COST_SCALE,
                                RoundingMode.HALF_UP),
                receipts.stream().map(Supply::getUnitCost).min(Comparator.naturalOrder()).orElseThrow(),
                receipts.stream().map(Supply::getUnitCost).max(Comparator.naturalOrder()).orElseThrow(),
                last.getUnitCost(),
                last.getMovementDate());
    }

    // -------------------------------------------------------------------- margin

    /**
     * Gross margin between two days, both included.
     *
     * @param from first day; defaults to the first of the current month
     * @param to   last day; defaults to today
     */
    public MarginReportResponse marginReport(LocalDate from, LocalDate to) {
        LocalDate last = to == null ? calendar.today() : to;
        LocalDate first = from == null ? last.withDayOfMonth(1) : from;
        if (first.isAfter(last)) {
            throw new BusinessRuleException("REPORT_PERIOD_INVERTED",
                    "La date de debut doit preceder la date de fin.");
        }
        if (first.plusDays(MAX_REPORT_DAYS).isBefore(last)) {
            throw new BusinessRuleException("REPORT_PERIOD_TOO_LONG",
                    "La periode ne peut pas depasser " + MAX_REPORT_DAYS + " jours.");
        }

        List<ProductMargin> byProduct = saleLines
                .aggregateMarginByProduct(first.atStartOfDay(), last.plusDays(1).atStartOfDay())
                .stream()
                .map(CostingService::marginOf)
                .sorted(Comparator.comparing(ProductMargin::grossMargin).reversed()
                        .thenComparing(ProductMargin::name))
                .toList();

        BigDecimal revenue = sum(byProduct, ProductMargin::revenue);
        BigDecimal cost = sum(byProduct, ProductMargin::costOfGoodsSold);
        BigDecimal margin = sum(byProduct, ProductMargin::grossMargin);
        BigDecimal costedRevenue = cost.add(margin);
        return new MarginReportResponse(
                first,
                last,
                revenue,
                costedRevenue,
                cost,
                margin,
                PurchaseCosting.marginRate(margin, costedRevenue),
                revenue.subtract(costedRevenue),
                revenue.signum() == 0 ? 100
                        : costedRevenue.multiply(BigDecimal.valueOf(100))
                                .divide(revenue, 0, RoundingMode.DOWN).intValue(),
                byProduct);
    }

    /** One row of {@link SaleLineRepository#aggregateMarginByProduct}. */
    private static ProductMargin marginOf(Object[] row) {
        BigDecimal revenue = decimal(row[3]);
        BigDecimal costedRevenue = decimal(row[4]);
        BigDecimal cost = decimal(row[5]);
        BigDecimal margin = costedRevenue.subtract(cost);
        return new ProductMargin(
                (Long) row[0],
                (String) row[1],
                ((Number) row[2]).longValue(),
                revenue,
                cost,
                margin,
                PurchaseCosting.marginRate(margin, costedRevenue),
                costedRevenue.compareTo(revenue) == 0);
    }

    /**
     * A sum as the database returned it. The {@code case} expressions mix an integer literal
     * with decimals, and which of the two the dialect hands back is its own business.
     */
    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> field) {
        return rows.stream().map(field).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
