package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.BaseUnitRequest;
import com.houssen.liberoshop.web.dto.PackagingRequest;
import com.houssen.liberoshop.web.dto.ProductUnitsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * The rules that keep a product's units unambiguous.
 *
 * <p>Each refusal below guards against a mistake that would not show on the day it is made: a
 * rounded factor drifts the stock a little on every sale, two units with the same name are two
 * buttons the cashier must guess between, and a barcode on two articles makes the scanner pick
 * one silently.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductUnitServiceTest {

    @Mock
    private ProductRepository products;
    @Mock
    private ProductPackagingRepository packagings;

    private ProductUnitService service;

    private final List<ProductPackaging> table = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(100);
    private Product rice;

    @BeforeEach
    void setUp() {
        service = new ProductUnitService(products, packagings);
        rice = Product.builder().id(1L).name("Riz").price(new BigDecimal("900"))
                .stockQuantity(1750).unit("kapoka").barcode("111").build();

        when(products.findById(anyLong())).thenAnswer(call ->
                call.getArgument(0).equals(rice.getId()) ? Optional.of(rice) : Optional.empty());
        when(products.existsByBarcode(anyString())).thenAnswer(call ->
                call.getArgument(0).equals(rice.getBarcode()));
        when(packagings.findByProductIdOrderByFactorAsc(anyLong())).thenAnswer(call -> table.stream()
                .filter(p -> p.getProduct().getId().equals(call.getArgument(0)))
                .sorted(Comparator.comparing(ProductPackaging::getFactor))
                .toList());
        when(packagings.findById(anyLong())).thenAnswer(call -> table.stream()
                .filter(p -> p.getId().equals(call.getArgument(0))).findFirst());
        when(packagings.existsByBarcode(anyString())).thenAnswer(call -> table.stream()
                .anyMatch(p -> call.getArgument(0).equals(p.getBarcode())));
        when(packagings.save(any(ProductPackaging.class))).thenAnswer(call -> {
            ProductPackaging saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(nextId.getAndIncrement());
                table.add(saved);
            }
            return saved;
        });
        doAnswer(call -> table.remove(call.<ProductPackaging>getArgument(0)))
                .when(packagings).delete(any(ProductPackaging.class));
    }

    private static PackagingRequest request(String label, String factor, String price, String barcode) {
        return new PackagingRequest(label, new BigDecimal(factor), new BigDecimal(price), barcode);
    }

    @Test
    @DisplayName("units come back smallest first, the base unit on the product")
    void listsSmallestFirst() {
        service.add(1L, request("sac 50 kg", "175", "145000", null));
        ProductUnitsResponse units = service.add(1L, request("kg", "3.5", "3000", null));

        assertEquals("kapoka", units.baseUnit());
        assertEquals(List.of("kg", "sac 50 kg"),
                units.packagings().stream().map(ProductUnitsResponse.Packaging::label).toList());
        assertEquals(0, new BigDecimal("3.5").compareTo(units.packagings().getFirst().factor()));
    }

    @Test
    @DisplayName("a label is trimmed and squeezed, and a blank barcode means none")
    void normalisesInput() {
        ProductUnitsResponse units = service.add(1L, request("  sac   50 kg ", "175", "145000", "  "));

        assertEquals("sac 50 kg", units.packagings().getFirst().label());
        assertNull(units.packagings().getFirst().barcode());
    }

    @Test
    @DisplayName("a label already used by the base unit or a sibling is refused, folded")
    void refusesDuplicateLabels() {
        service.add(1L, request("Sac 50 kg", "175", "145000", null));

        assertEquals("PACKAGING_LABEL_TAKEN", assertThrows(BusinessRuleException.class,
                () -> service.add(1L, request("sac50kg", "150", "120000", null))).code());
        assertEquals("PACKAGING_LABEL_TAKEN", assertThrows(BusinessRuleException.class,
                () -> service.add(1L, request("Kapoka", "2", "1800", null))).code());
    }

    @Test
    @DisplayName("an edit may keep its own label")
    void editKeepsItsLabel() {
        Long id = service.add(1L, request("kg", "3.5", "3000", null)).packagings().getFirst().id();

        ProductUnitsResponse units = service.update(1L, id, request("kg", "3.5", "3200", null));

        assertEquals(0, new BigDecimal("3200").compareTo(units.packagings().getFirst().price()));
    }

    @Test
    @DisplayName("the base unit cannot be renamed onto a packaging's label")
    void baseRenameRefusesPackagingLabel() {
        service.add(1L, request("kg", "3.5", "3000", null));

        assertThrows(BusinessRuleException.class, () -> service.renameBaseUnit(1L, new BaseUnitRequest("KG")));
        assertEquals("gobelet", service.renameBaseUnit(1L, new BaseUnitRequest(" gobelet ")).baseUnit());
        assertNull(service.renameBaseUnit(1L, new BaseUnitRequest("  ")).baseUnit());
    }

    @Test
    @DisplayName("a factor must be positive, not 1, and at most three decimals -- never rounded")
    void refusesBadFactors() {
        assertEquals("PACKAGING_FACTOR_INVALID", assertThrows(BusinessRuleException.class,
                () -> service.add(1L, request("kg", "0", "3000", null))).code());
        assertEquals("PACKAGING_FACTOR_IS_BASE", assertThrows(BusinessRuleException.class,
                () -> service.add(1L, request("pièce", "1.000", "900", null))).code());
        assertEquals("PACKAGING_FACTOR_TOO_PRECISE", assertThrows(BusinessRuleException.class,
                () -> service.add(1L, request("demi", "0.2857", "250", null))).code());
        // Trailing zeros are not precision.
        service.add(1L, request("kg", "3.5000", "3000", null));
    }

    @Test
    @DisplayName("a price must be positive")
    void refusesFreeUnits() {
        assertEquals("PACKAGING_PRICE_INVALID", assertThrows(BusinessRuleException.class,
                () -> service.add(1L, request("kg", "3.5", "0", null))).code());
    }

    @Test
    @DisplayName("a barcode already on a product or another unit is refused, but a unit keeps its own")
    void refusesSharedBarcodes() {
        Long id = service.add(1L, request("sac 50 kg", "175", "145000", "222")).packagings().getFirst().id();

        assertEquals("BARCODE_TAKEN", assertThrows(BusinessRuleException.class,
                () -> service.add(1L, request("kg", "3.5", "3000", "111"))).code());
        assertEquals("BARCODE_TAKEN", assertThrows(BusinessRuleException.class,
                () -> service.add(1L, request("kg", "3.5", "3000", "222"))).code());
        service.update(1L, id, request("sac 50 kg", "175", "140000", "222"));
    }

    @Test
    @DisplayName("a refused edit leaves the unit as it was")
    void refusalChangesNothing() {
        Long id = service.add(1L, request("kg", "3.5", "3000", null)).packagings().getFirst().id();

        assertThrows(BusinessRuleException.class, () -> service.update(1L, id, request("litre", "3.5", "0", null)));

        assertEquals("kg", table.getFirst().getLabel());
    }

    @Test
    @DisplayName("a unit is only reachable through its own product")
    void unitBelongsToItsProduct() {
        Long id = service.add(1L, request("kg", "3.5", "3000", null)).packagings().getFirst().id();

        assertThrows(ResourceNotFoundException.class, () -> service.remove(2L, id));
        assertEquals(0, service.remove(1L, id).packagings().size());
    }
}
