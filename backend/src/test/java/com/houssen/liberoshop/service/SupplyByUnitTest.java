package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import com.houssen.liberoshop.web.dto.SupplierPriceResponse;
import com.houssen.liberoshop.web.dto.SupplyResponse;
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
 * Rice received by the 50 kg sack, sold by the kapoka.
 *
 * <p>The receipt must read like the supplier's invoice -- ten sacks at 145 000 -- while the stock
 * and the average cost move in base units, where the sales draw from them.
 */
@SpringBootTest
class SupplyByUnitTest {

    @Autowired
    private StockService stockService;
    @Autowired
    private CostingService costingService;
    @Autowired
    private ProductRepository products;
    @Autowired
    private ProductPackagingRepository packagings;
    @Autowired
    private SupplierRepository suppliers;
    @Autowired
    private UserAppRepository users;

    private Product rice;
    private ProductPackaging sack;
    private Supplier wholesaler;
    private UserApp manager;

    @BeforeEach
    void looseRice() {
        rice = products.save(Product.builder()
                .name("Riz vrac " + UUID.randomUUID().toString().substring(0, 6))
                .unit("kapoka")
                .price(new BigDecimal("1000"))
                .stockQuantity(new BigDecimal("0"))
                .build());
        sack = packagings.save(ProductPackaging.builder().product(rice).label("sac 50 kg")
                .factor(new BigDecimal("175.000")).price(new BigDecimal("160000")).build());
        wholesaler = suppliers.save(Supplier.builder().name("Grossiste " + UUID.randomUUID()).build());
        String handle = "m" + UUID.randomUUID().toString().substring(0, 8);
        manager = users.save(UserApp.builder().fullName("Gestion " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.DEPOT_MANAGER)).build());
    }

    private SupplyResponse receive(Long packagingId, String quantity, String unitCost) {
        return stockService.registerSupply(new CreateSupplyRequest(rice.getId(), wholesaler.getId(),
                packagingId, new BigDecimal(quantity), unitCost == null ? null : new BigDecimal(unitCost)),
                manager);
    }

    private Product reloaded() {
        return products.findById(rice.getId()).orElseThrow();
    }

    @Test
    @DisplayName("10 sacks at 145 000: the stock rises by 1 750 kapoka, the receipt says 10 sacs")
    void receivesInAPackaging() {
        SupplyResponse supply = receive(sack.getId(), "10", "145000");

        assertQuantity(1750, reloaded().getStockQuantity());
        assertQuantity(1750, supply.quantity());
        assertQuantity(10, supply.receivedQuantity());
        assertEquals("sac 50 kg", supply.unitLabel());
        assertEquals(0, new BigDecimal("145000").compareTo(supply.unitCost()));
        // The invoice's line exactly, not 828.57 × 1 750.
        assertEquals(0, new BigDecimal("1450000").compareTo(supply.totalCost()));
    }

    @Test
    @DisplayName("the average cost is kept per base unit: a sack at 145 000 is 828.57 a kapoka")
    void averagesPerBaseUnit() {
        receive(sack.getId(), "10", "145000");
        assertEquals(new BigDecimal("828.57"), reloaded().getAverageCost());

        // 175 kapoka more at 700 each, received loose: (1 750 × 828.57 + 175 × 700) / 1 925.
        receive(null, "175", "700");
        assertEquals(new BigDecimal("816.88"), reloaded().getAverageCost());
    }

    @Test
    @DisplayName("suppliers are compared per base unit, whatever unit each delivered in")
    void comparesSuppliersPerBaseUnit() {
        receive(sack.getId(), "2", "140000");

        List<SupplierPriceResponse> prices = costingService.supplierPricesOf(rice.getId());

        assertEquals(new BigDecimal("800.00"), prices.getFirst().averageCost());
        assertEquals(new BigDecimal("800.00"), prices.getFirst().lastUnitCost());
        assertQuantity(350, prices.getFirst().units());
    }

    @Test
    @DisplayName("a receipt in the base unit stays unlabelled, as before units")
    void baseUnitReceiptsAreUnlabelled() {
        SupplyResponse supply = receive(null, "12.5", null);

        assertNull(supply.unitLabel());
        assertQuantity("12.5", supply.receivedQuantity());
        assertNull(supply.totalCost());
        assertQuantity("12.5", reloaded().getStockQuantity());
    }

    @Test
    @DisplayName("another product's unit is refused")
    void refusesAForeignUnit() {
        Product other = products.save(Product.builder().name("Autre " + UUID.randomUUID())
                .price(BigDecimal.TEN).stockQuantity(BigDecimal.ZERO).build());

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> stockService.registerSupply(new CreateSupplyRequest(other.getId(), wholesaler.getId(),
                        sack.getId(), BigDecimal.ONE, null), manager));
        assertEquals("UNKNOWN_SALE_UNIT", refused.code());
    }
}
