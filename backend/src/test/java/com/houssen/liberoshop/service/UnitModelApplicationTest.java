package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.ApplyUnitModelRequest;
import com.houssen.liberoshop.web.dto.CreateProductRequest;
import com.houssen.liberoshop.web.dto.CreateUnitModelRequest;
import com.houssen.liberoshop.web.dto.PackagingRequest;
import com.houssen.liberoshop.web.dto.ProductResponse;
import com.houssen.liberoshop.web.dto.ProductUnitsResponse;
import com.houssen.liberoshop.web.dto.UnitModelResponse;
import com.houssen.liberoshop.web.dto.UnitModelUnitRequest;
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
import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ticking units of the "Poids" model (g, kg, tonne) on a product: each lands as an ordinary unit
 * of the product, at the price typed, counted in the product's own base unit.
 */
@SpringBootTest
class UnitModelApplicationTest {

    @Autowired
    private UnitModelService models;
    @Autowired
    private ProductUnitService units;
    @Autowired
    private ProductEditService productEditService;
    @Autowired
    private ProductRepository products;
    @Autowired
    private UserAppRepository users;

    private UnitModelResponse weight;

    @BeforeEach
    void aWeightModel() {
        weight = models.create(new CreateUnitModelRequest("Poids " + UUID.randomUUID().toString().substring(0, 8),
                "g", List.of(new UnitModelUnitRequest("kg", new BigDecimal("1000"), null),
                        new UnitModelUnitRequest("tonne", new BigDecimal("1000"), "kg"))));
    }

    private Product product(String unit) {
        return products.save(Product.builder().name("Produit " + UUID.randomUUID().toString().substring(0, 8))
                .unit(unit).price(new BigDecimal("1000")).stockQuantity(qty(0)).build());
    }

    private static ApplyUnitModelRequest.Choice tick(String label, String price) {
        return new ApplyUnitModelRequest.Choice(label, new BigDecimal(price));
    }

    private ApplyUnitModelRequest ticked(ApplyUnitModelRequest.Choice... choices) {
        return new ApplyUnitModelRequest(weight.id(), null, null, List.of(choices));
    }

    private static ProductResponse.Packaging unit(ProductUnitsResponse response, String label) {
        return response.packagings().stream().filter(p -> p.label().equals(label)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a product counted in kg takes the gram and the tonne without saying anything more")
    void produitCompteEnKg() {
        Product sugar = product("kg");

        ProductUnitsResponse result = units.addFromModel(sugar.getId(),
                ticked(tick("g", "5"), tick("tonne", "4000000")));

        assertQuantity("0.001", unit(result, "g").factor());
        // kg is the product's base unit: "1000 kg" is written straight in it.
        assertNull(unit(result, "tonne").reference());
        assertQuantity(1000, unit(result, "tonne").factor());
        assertEquals(0, new BigDecimal("4000000").compareTo(unit(result, "tonne").price()));
    }

    @Test
    @DisplayName("rice counted in kapoka meets the model through \"1 kg = 3,5 kapoka\"")
    void produitAvecAncre() {
        Product rice = product("kapoka");

        ProductUnitsResponse result = units.addFromModel(rice.getId(), new ApplyUnitModelRequest(weight.id(),
                "kg", new BigDecimal("3.5"), List.of(tick("kg", "3000"), tick("tonne", "2900000"))));

        assertQuantity("3.5", unit(result, "kg").factor());
        assertNull(unit(result, "kg").reference());
        assertQuantity(3500, unit(result, "tonne").factor());
    }

    @Test
    @DisplayName("a product that already has its kg needs no anchor: its kg says it")
    void produitQuiADejaLeKg() {
        Product rice = product("kapoka");
        units.add(rice.getId(), new PackagingRequest("kg", new BigDecimal("3.5"), new BigDecimal("3000"), null));

        ProductUnitsResponse result = units.addFromModel(rice.getId(), ticked(tick("tonne", "2900000")));

        assertQuantity(3500, unit(result, "tonne").factor());
        // Written the way the model writes it: it follows the product's kg if that is corrected.
        assertEquals("kg", unit(result, "tonne").reference());
    }

    @Test
    @DisplayName("without an anchor nor a shared unit, the model cannot be placed")
    void ancreManquante() {
        Product rice = product("kapoka");

        assertEquals("UNIT_MODEL_ANCHOR_REQUIRED", assertThrows(BusinessRuleException.class,
                () -> units.addFromModel(rice.getId(), ticked(tick("kg", "3000")))).code());
    }

    @Test
    @DisplayName("a gram of rice in kapoka (0,0035) is refused, and nothing else is added with it")
    void toutOuRien() {
        Product rice = product("kapoka");

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> units.addFromModel(rice.getId(), new ApplyUnitModelRequest(weight.id(), "kg",
                        new BigDecimal("3.5"), List.of(tick("g", "3"), tick("kg", "3000")))));

        assertEquals("PACKAGING_FACTOR_TOO_PRECISE", refused.code());
        assertTrue(refused.getMessage().startsWith("\"g\""));
        assertTrue(units.unitsOf(rice.getId()).packagings().isEmpty());
    }

    @Test
    @DisplayName("a unit the product already has is not added a second time")
    void uniteDejaPresente() {
        Product sugar = product("kg");
        units.add(sugar.getId(), new PackagingRequest("tonne", new BigDecimal("1000"), new BigDecimal("4000000"), null));

        assertEquals("UNIT_MODEL_UNIT_PRESENT", assertThrows(BusinessRuleException.class,
                () -> units.addFromModel(sugar.getId(), ticked(tick("tonne", "4000000")))).code());
    }

    @Test
    @DisplayName("a product typed in by hand is created with the units ticked in a model")
    void creationAvecUnites() {
        String handle = "m" + UUID.randomUUID().toString().substring(0, 8);
        UserApp manager = users.save(UserApp.builder().fullName("Depot " + handle).username(handle).password("x")
                .enabled(true).roles(EnumSet.of(RoleApp.DEPOT_MANAGER)).build());

        ProductResponse created = productEditService.create(new CreateProductRequest(
                "Farine " + handle, new BigDecimal("1200"), "kg", null, null, null, null,
                ticked(tick("tonne", "1100000"))), manager);

        assertQuantity(1000, unit(units.unitsOf(created.id()), "tonne").factor());
    }
}
