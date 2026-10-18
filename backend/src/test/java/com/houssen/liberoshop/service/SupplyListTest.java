package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.web.dto.CreateSupplyRequest;
import com.houssen.liberoshop.web.dto.SupplyResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The receipts are read a few at a time: the latest ones, or one supplier's. */
@SpringBootTest
class SupplyListTest {

    @Autowired
    private StockService stock;
    @Autowired
    private ProductRepository products;
    @Autowired
    private SupplierRepository suppliers;
    @Autowired
    private UserAppRepository users;

    private UserApp manager() {
        String handle = "m" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(UserApp.builder().fullName("Depot " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.DEPOT_MANAGER)).build());
    }

    @Test
    @DisplayName("les achats d'un fournisseur sont filtres par la base : ni ceux des autres, ni les imports sans fournisseur")
    void achatsDUnFournisseur() {
        Product riz = products.save(Product.builder().name("Riz " + UUID.randomUUID().toString().substring(0, 6))
                .price(new BigDecimal("1000")).stockQuantity(qty(0)).build());
        Supplier sotema = suppliers.save(Supplier.builder().name("Sotema " + UUID.randomUUID()).build());
        Supplier other = suppliers.save(Supplier.builder().name("Autre " + UUID.randomUUID()).build());
        UserApp nadia = manager();

        stock.registerSupply(new CreateSupplyRequest(riz.getId(), sotema.getId(), qty(10), null), nadia);
        stock.registerSupply(new CreateSupplyRequest(riz.getId(), other.getId(), qty(4), null), nadia);
        stock.registerSupply(new CreateSupplyRequest(riz.getId(), null, qty(2), null), nadia);
        SupplyResponse latest = stock.registerSupply(
                new CreateSupplyRequest(riz.getId(), sotema.getId(), qty(6), null), nadia);

        List<SupplyResponse> fromSotema = stock.findSuppliesOf(sotema.getId());
        assertEquals(2, fromSotema.size());
        assertEquals(latest.id(), fromSotema.get(0).id(), "le plus recent d'abord");
    }

    @Test
    @DisplayName("l'ecran liste les derniers approvisionnements, jamais plus que la limite")
    void derniersApprovisionnementsBornes() {
        List<SupplyResponse> latest = stock.findSupplies();
        assertTrue(latest.size() <= StockService.LATEST_LIMIT);
    }
}
