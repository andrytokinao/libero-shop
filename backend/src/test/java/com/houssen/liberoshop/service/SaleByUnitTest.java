package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.PaymentMethod;
import com.houssen.liberoshop.entity.PaymentStatus;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.SaleLine;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SaleLineRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.InsufficientStockException;
import com.houssen.liberoshop.web.dto.CreateSaleRequest;
import com.houssen.liberoshop.web.dto.InvoiceResponse;
import com.houssen.liberoshop.web.dto.SaleLineResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.assertQuantity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Loose rice, counted in kapoka and sold by the kapoka, the kilo or the sack.
 *
 * <p>What matters here is what nobody sees at the till: the stock moves by the base units each
 * line holds, the receipt keeps the unit it was sold in, and the margin is costed per unit sold.
 */
@SpringBootTest
class SaleByUnitTest {

    @Autowired
    private SaleService saleService;
    @Autowired
    private ProductRepository products;
    @Autowired
    private ProductPackagingRepository packagings;
    @Autowired
    private StockMovementRepository movements;
    @Autowired
    private SaleLineRepository saleLines;
    @Autowired
    private UserAppRepository users;

    private Product rice;
    private ProductPackaging kilo;
    private ProductPackaging sack;
    private UserApp cashier;

    @BeforeEach
    void looseRice() {
        rice = products.save(Product.builder()
                .name("Riz vrac " + UUID.randomUUID().toString().substring(0, 6))
                .unit("kapoka")
                .price(new BigDecimal("900"))
                .averageCost(new BigDecimal("700.00"))
                .stockQuantity(new BigDecimal("100"))
                .build());
        kilo = packagings.save(ProductPackaging.builder().product(rice).label("kg")
                .factor(new BigDecimal("3.500")).price(new BigDecimal("3000")).build());
        sack = packagings.save(ProductPackaging.builder().product(rice).label("sac 50 kg")
                .factor(new BigDecimal("175.000")).price(new BigDecimal("145000")).build());
        String handle = "c" + UUID.randomUUID().toString().substring(0, 8);
        cashier = users.save(UserApp.builder().fullName("Caisse " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.CASHIER)).build());
    }

    private InvoiceResponse sell(CreateSaleRequest.Line... lines) {
        return saleService.checkout(new CreateSaleRequest("Client", PaymentStatus.PAID,
                PaymentMethod.CASH, List.of(lines)), cashier);
    }

    private BigDecimal stock() {
        return products.findById(rice.getId()).orElseThrow().getStockQuantity();
    }

    @Test
    @DisplayName("2 kg and 1 kapoka: priced per unit sold, the stock drops by 8 kapoka")
    void sellsInSeveralUnits() {
        InvoiceResponse invoice = sell(
                new CreateSaleRequest.Line(rice.getId(), kilo.getId(), new BigDecimal("2")),
                new CreateSaleRequest.Line(rice.getId(), new BigDecimal("1")));

        assertEquals(0, new BigDecimal("6900").compareTo(invoice.sale().totalAmount()));
        SaleLineResponse kilos = invoice.sale().lines().getFirst();
        assertEquals("kg", kilos.unitLabel());
        assertQuantity("3.5", kilos.unitFactor());
        assertQuantity(2, kilos.quantity());
        assertEquals("kapoka", invoice.sale().lines().get(1).unitLabel());
        assertQuantity(92, stock());
        // What left the shelf is written in base units, so the history adds up with the stock.
        assertQuantity(7, movements.findOutputsOfInvoice(invoice.id()).getFirst().getQuantity());
    }

    @Test
    @DisplayName("half a kilo is 1.75 kapoka off the shelf and half the kilo's price")
    void sellsAFraction() {
        InvoiceResponse invoice = sell(
                new CreateSaleRequest.Line(rice.getId(), kilo.getId(), new BigDecimal("0.5")));

        assertEquals(0, new BigDecimal("1500").compareTo(invoice.sale().totalAmount()));
        assertQuantity("98.25", stock());
    }

    @Test
    @DisplayName("a sack draws 175 kapoka: refused against 100, in base units, naming the unit")
    void checksTheStockInBaseUnits() {
        InsufficientStockException refused = assertThrows(InsufficientStockException.class,
                () -> sell(new CreateSaleRequest.Line(rice.getId(), sack.getId(), BigDecimal.ONE)));

        InsufficientStockException.Shortage shortage = refused.shortages().getFirst();
        assertQuantity(175, shortage.requested());
        assertQuantity(100, shortage.available());
        assertEquals("kapoka", shortage.unit());
        assertQuantity(100, stock());
    }

    @Test
    @DisplayName("lines of one product in two units are checked together against one shelf")
    void addsUnitsUpBeforeChecking() {
        // 20 kg is 70 kapoka and 40 kapoka more is 110: each fits alone, not together.
        assertThrows(InsufficientStockException.class, () -> sell(
                new CreateSaleRequest.Line(rice.getId(), kilo.getId(), new BigDecimal("20")),
                new CreateSaleRequest.Line(rice.getId(), new BigDecimal("40"))));
        assertQuantity(100, stock());
    }

    @Test
    @DisplayName("the cost frozen on a line is the base unit's average times the unit's factor")
    void costsPerUnitSold() {
        sell(new CreateSaleRequest.Line(rice.getId(), kilo.getId(), BigDecimal.ONE));

        // 700 a kapoka, 3.5 kapoka a kilo.
        SaleLine line = saleLines.findAll().stream()
                .filter(candidate -> candidate.getProduct().getId().equals(rice.getId()))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("2450.00").compareTo(line.getUnitCost()));
    }

    @Test
    @DisplayName("another product's unit, or a unit deleted since, is refused")
    void refusesAForeignUnit() {
        Product other = products.save(Product.builder().name("Autre " + UUID.randomUUID())
                .price(BigDecimal.TEN).stockQuantity(new BigDecimal("50")).build());

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> sell(new CreateSaleRequest.Line(other.getId(), kilo.getId(), BigDecimal.ONE)));
        assertEquals("UNKNOWN_SALE_UNIT", refused.code());
    }

    @Test
    @DisplayName("a quantity finer than a thousandth is refused rather than rounded")
    void refusesTooPreciseQuantities() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> sell(new CreateSaleRequest.Line(rice.getId(), new BigDecimal("0.0005"))));
        assertEquals("QUANTITY_TOO_PRECISE", refused.code());
    }

    @Test
    @DisplayName("a product without a named unit sells as before, its lines unlabelled")
    void bareUnitsStillSell() {
        Product soap = products.save(Product.builder().name("Savon " + UUID.randomUUID())
                .price(new BigDecimal("1500")).stockQuantity(new BigDecimal("10")).build());

        InvoiceResponse invoice = sell(new CreateSaleRequest.Line(soap.getId(), new BigDecimal("3")));

        assertNull(invoice.sale().lines().getFirst().unitLabel());
        assertQuantity(1, invoice.sale().lines().getFirst().unitFactor());
        assertQuantity(7, products.findById(soap.getId()).orElseThrow().getStockQuantity());
    }
}
