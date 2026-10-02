package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.Sale;
import com.houssen.liberoshop.entity.SaleLine;
import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SaleLineRepository;
import com.houssen.liberoshop.repository.SaleRepository;
import com.houssen.liberoshop.web.dto.StockValuationResponse;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.MarginReportResponse;
import com.houssen.liberoshop.web.dto.ProductCostResponse;
import com.houssen.liberoshop.web.dto.SupplierPriceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.houssen.liberoshop.util.QuantityAssertions.assertQuantity;
import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Costs and margins, against a real database.
 *
 * <p>Run on H2 rather than on mocks because the figures come out of grouped JPQL: a mock would
 * only return the rows the test itself wrote, and the question is precisely whether the query
 * sums, filters and leaves out the uncosted lines as intended.
 *
 * <p>The database is a pooled in-memory one rather than the slice's default embedded database.
 * H2 2.4 binds a table's check constraints to the connection that created the table, and the
 * default opens and closes a connection per use: every insert into {@code stock_movement} then
 * fails its single-table check with "the database has been closed". A pool keeps the creating
 * connection alive, as it does in the application itself.
 */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:costing;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CostingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    @Autowired
    private TestEntityManager db;
    @Autowired
    private ProductRepository products;
    @Autowired
    private StockMovementRepository movements;
    @Autowired
    private SaleLineRepository saleLines;
    @Autowired
    private SaleRepository sales;
    @Autowired
    private CategoryRepository categories;

    private CostingService service;
    private UserApp nadia;
    private Product riz;
    private Product sel;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        service = new CostingService(products, categories, movements, sales, saleLines,
                new BusinessCalendar(clock));

        nadia = db.persist(UserApp.builder()
                .fullName("Nadia Rasolofo")
                .username("nadia")
                .password("x")
                .enabled(true)
                .roles(EnumSet.of(RoleApp.DEPOT_MANAGER))
                .build());
        riz = db.persist(Product.builder().name("Riz 5kg").price(new BigDecimal("12500"))
                .stockQuantity(qty(50)).averageCost(new BigDecimal("10200.00")).build());
        sel = db.persist(Product.builder().name("Sel 1kg").price(new BigDecimal("900"))
                .stockQuantity(qty(30)).build());
    }

    // ------------------------------------------------------------------ fixtures

    private Supplier supplier(String name) {
        return db.persist(Supplier.builder().name(name).build());
    }

    private void receipt(Product product, Supplier from, long quantity, String unitCost,
                         LocalDateTime when) {
        db.persist(Supply.builder()
                .product(product)
                .supplier(from)
                .quantity(qty(quantity))
                .unitCost(unitCost == null ? null : new BigDecimal(unitCost))
                .movementDate(when)
                .performedBy(nadia)
                .build());
    }

    private void sale(LocalDateTime when, PaymentStatus status, Object... lines) {
        Sale sale = Sale.builder()
                .saleDate(when)
                .paymentStatus(status)
                .seller(nadia)
                .totalAmount(BigDecimal.ZERO)
                .lines(new ArrayList<>())
                .build();
        for (int i = 0; i < lines.length; i += 4) {
            sale.getLines().add(SaleLine.builder()
                    .sale(sale)
                    .product((Product) lines[i])
                    .quantity(qty((Integer) lines[i + 1]))
                    .unitPrice(new BigDecimal((String) lines[i + 2]))
                    .unitCost(lines[i + 3] == null ? null : new BigDecimal((String) lines[i + 3]))
                    .build());
        }
        sale.calculateTotal();
        db.persist(sale);
    }

    // -------------------------------------------------------------------- margin

    @Test
    @DisplayName("margin is sale price minus frozen cost, summed over the period")
    void grossMargin() {
        sale(TODAY.atTime(9, 0), PaymentStatus.PAID, riz, 2, "12500", "10000");
        sale(TODAY.minusDays(3).atTime(9, 0), PaymentStatus.UNPAID, riz, 1, "12500", "10200");
        db.flush();

        MarginReportResponse report = service.marginReport(TODAY.withDayOfMonth(1), TODAY);

        assertEquals(0, new BigDecimal("37500").compareTo(report.revenue()));
        assertEquals(0, new BigDecimal("30200").compareTo(report.costOfGoodsSold()));
        assertEquals(0, new BigDecimal("7300").compareTo(report.grossMargin()));
        assertEquals(new BigDecimal("19.5"), report.marginRate());
        assertEquals(100, report.coveragePercent());
        assertQuantity(3, report.byProduct().getFirst().unitsSold());
        assertEquals(0, new BigDecimal("25000").compareTo(report.paidRevenue()),
                "the unpaid sale is turnover, not cash");
    }

    @Test
    @DisplayName("lines sold without a known cost are reported apart, never counted as profit")
    void uncostedLinesStayOut() {
        sale(TODAY.atTime(9, 0), PaymentStatus.PAID,
                riz, 1, "12500", "10000",
                sel, 10, "900", null);
        db.flush();

        MarginReportResponse report = service.marginReport(TODAY, TODAY);

        assertEquals(0, new BigDecimal("21500").compareTo(report.revenue()));
        assertEquals(0, new BigDecimal("2500").compareTo(report.grossMargin()));
        assertEquals(0, new BigDecimal("9000").compareTo(report.uncostedRevenue()));
        assertEquals(58, report.coveragePercent());

        MarginReportResponse.ProductMargin salt = report.byProduct().stream()
                .filter(row -> row.name().equals("Sel 1kg")).findFirst().orElseThrow();
        assertFalse(salt.fullyCosted());
        assertNull(salt.marginRate());
    }

    @Test
    @DisplayName("the period includes its last day and nothing after it")
    void periodBounds() {
        sale(TODAY.minusDays(1).atTime(23, 59), PaymentStatus.PAID, riz, 1, "12500", "10000");
        sale(TODAY.atTime(0, 0), PaymentStatus.PAID, riz, 5, "12500", "10000");
        db.flush();

        MarginReportResponse report = service.marginReport(TODAY.minusDays(1), TODAY.minusDays(1));

        assertEquals(0, new BigDecimal("2500").compareTo(report.grossMargin()));
    }

    @Test
    @DisplayName("refuses a period that ends before it starts")
    void invertedPeriod() {
        assertThrows(BusinessRuleException.class,
                () -> service.marginReport(TODAY, TODAY.minusDays(1)));
    }

    // ----------------------------------------------------------------- valuation

    @Test
    @DisplayName("values the shelves at cost and at sale price, the difference being the profit waiting")
    void stockValuation() {
        Category epicerie = db.persist(Category.builder().name("Epicerie").build());
        riz.setCategory(epicerie);
        db.persist(Product.builder().name("Vide").price(new BigDecimal("500"))
                .stockQuantity(qty(0)).averageCost(new BigDecimal("400")).category(epicerie).build());
        db.flush();

        StockValuationResponse valuation = service.stockValuation();

        // riz: 50 × 10 200 at cost, 50 × 12 500 at sale price; sel: 30 × 900, never costed.
        assertEquals(0, new BigDecimal("510000").compareTo(valuation.costValue()));
        assertEquals(0, new BigDecimal("652000").compareTo(valuation.saleValue()));
        assertEquals(0, new BigDecimal("115000").compareTo(valuation.potentialMargin()));
        assertEquals(new BigDecimal("18.4"), valuation.potentialMarginRate());
        assertQuantity(30, valuation.uncostedUnits());
        assertEquals(95, valuation.coveragePercent());
        assertEquals(2, valuation.references(), "an empty shelf is not stock");

        StockValuationResponse.CategoryValuation first = valuation.byCategory().getFirst();
        assertEquals("Epicerie", first.path());
        assertQuantity(0, first.uncostedUnits());
        StockValuationResponse.CategoryValuation loose = valuation.byCategory().getLast();
        assertEquals(CostingService.NO_CATEGORY_LABEL, loose.path());
        assertQuantity(30, loose.uncostedUnits());
    }

    // ------------------------------------------------------------------ products

    @Test
    @DisplayName("each product carries its average cost, unit margin and last price paid")
    void productCosts() {
        Supplier analakely = supplier("Grossiste Analakely");
        receipt(riz, analakely, 40, "10000", TODAY.minusDays(10).atStartOfDay());
        receipt(riz, analakely, 10, "11000", TODAY.minusDays(2).atStartOfDay());
        receipt(riz, analakely, 5, null, TODAY.minusDays(1).atStartOfDay());
        db.flush();

        List<ProductCostResponse> costs = service.findProductCosts();
        ProductCostResponse rice = costs.stream()
                .filter(row -> row.productId().equals(riz.getId())).findFirst().orElseThrow();
        ProductCostResponse salt = costs.stream()
                .filter(row -> row.productId().equals(sel.getId())).findFirst().orElseThrow();

        assertEquals(0, new BigDecimal("11000").compareTo(rice.lastUnitCost()),
                "the uncosted receipt after it does not hide the last known price");
        assertEquals("Grossiste Analakely", rice.lastSupplierName());
        assertEquals(0, new BigDecimal("2300").compareTo(rice.unitMargin()));
        assertNull(salt.averageCost());
        assertNull(salt.lastUnitCost());
    }

    @Test
    @DisplayName("compares suppliers on their unit-weighted average, cheapest first")
    void supplierComparison() {
        Supplier analakely = supplier("Grossiste Analakely");
        Supplier tana = supplier("Tana Distribution");
        receipt(riz, analakely, 10, "10500", TODAY.minusDays(20).atStartOfDay());
        receipt(riz, analakely, 30, "10900", TODAY.minusDays(5).atStartOfDay());
        receipt(riz, tana, 20, "10300", TODAY.minusDays(8).atStartOfDay());
        receipt(riz, null, 5, "9000", TODAY.minusDays(30).atStartOfDay());
        db.flush();

        List<SupplierPriceResponse> prices = service.supplierPricesOf(riz.getId());

        assertEquals(List.of(CostingService.NO_SUPPLIER_LABEL, "Tana Distribution",
                        "Grossiste Analakely"),
                prices.stream().map(SupplierPriceResponse::supplierName).toList());

        SupplierPriceResponse grossiste = prices.getLast();
        assertEquals(2, grossiste.receipts());
        assertQuantity(40, grossiste.units());
        // (10 × 10 500 + 30 × 10 900) / 40
        assertEquals(new BigDecimal("10800.00"), grossiste.averageCost());
        assertEquals(0, new BigDecimal("10900").compareTo(grossiste.lastUnitCost()));
        assertEquals(0, new BigDecimal("10500").compareTo(grossiste.lowestCost()));
        assertTrue(grossiste.lastPurchaseDate().isAfter(TODAY.minusDays(6).atStartOfDay()));
    }
}
