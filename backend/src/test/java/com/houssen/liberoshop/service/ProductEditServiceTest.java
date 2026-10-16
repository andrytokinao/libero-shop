package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.UnitModelRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.ProductUnitsResponse;
import com.houssen.liberoshop.web.dto.UpdateProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Correcting a product after it was created -- and what a correction must never do: take
 * another article's barcode, or leave the product without a name or a price to sell at.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductEditServiceTest {

    @Mock
    private ProductRepository products;
    @Mock
    private ProductPackagingRepository packagings;
    @Mock
    private CategoryRepository categories;
    @Mock
    private StockMovementRepository movements;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private UnitModelRepository unitModels;

    private ProductEditService service;
    private Product rice;

    @BeforeEach
    void setUp() {
        service = new ProductEditService(products, packagings, categories, movements,
                new PackagingRules(products, packagings), new BusinessCalendar(Clock.systemDefaultZone()), events,
                new UnitModelApplication(unitModels, packagings, new PackagingRules(products, packagings)));
        rice = Product.builder().id(1L).name("Riz").price(new BigDecimal("900"))
                .stockQuantity(qty(1750)).unit("kapoka").barcode("111").build();

        when(products.findByIdForUpdate(anyLong())).thenAnswer(call ->
                call.getArgument(0).equals(rice.getId()) ? Optional.of(rice) : Optional.empty());
        when(products.existsByBarcode(anyString())).thenAnswer(call ->
                List.of("111", "222").contains(call.<String>getArgument(0)));
        when(packagings.existsByBarcode(anyString())).thenAnswer(call ->
                call.getArgument(0).equals("333"));
        when(packagings.findByProductIdOrderByFactorAsc(anyLong())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Le nom, le prix et le code-barres sont corriges, le stock ne bouge pas")
    void editsTheSheet() {
        ProductUnitsResponse response = service.edit(1L,
                new UpdateProductRequest("  Riz   rose ", new BigDecimal("950"), " 444 ", null, null, null));

        assertEquals("Riz rose", rice.getName());
        assertEquals(new BigDecimal("950"), rice.getPrice());
        assertEquals("444", rice.getBarcode());
        assertEquals(qty(1750), rice.getStockQuantity());
        assertEquals("Riz rose", response.productName());
        assertEquals(new BigDecimal("950"), response.basePrice());
    }

    @Test
    @DisplayName("Les ecrans ouverts sont prevenus du nouveau prix")
    void tellsTheScreens() {
        service.edit(1L, new UpdateProductRequest("Riz", new BigDecimal("950"), "111", null, null, null));

        ArgumentCaptor<StockChangedEvent> event = ArgumentCaptor.forClass(StockChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals(List.of(1L), event.getValue().productIds());
    }

    @Test
    @DisplayName("Garder son propre code-barres n'est pas le prendre a un autre")
    void keepsItsOwnBarcode() {
        service.edit(1L, new UpdateProductRequest("Riz", new BigDecimal("950"), "111", null, null, null));

        assertEquals("111", rice.getBarcode());
    }

    @Test
    @DisplayName("Un code-barres vide retire le code")
    void blankBarcodeClearsIt() {
        service.edit(1L, new UpdateProductRequest("Riz", new BigDecimal("900"), "  ", null, null, null));

        assertNull(rice.getBarcode());
    }

    @Test
    @DisplayName("Le code-barres d'un autre produit ou d'une autre unite est refuse")
    void refusesATakenBarcode() {
        for (String taken : List.of("222", "333")) {
            BusinessRuleException refused = assertThrows(BusinessRuleException.class, () ->
                    service.edit(1L, new UpdateProductRequest("Riz", new BigDecimal("900"), taken, null, null, null)));
            assertEquals("BARCODE_TAKEN", refused.code());
        }
        assertEquals("111", rice.getBarcode());
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Un prix nul ou negatif est refuse, rien n'est ecrit")
    void refusesANonPositivePrice() {
        for (String price : List.of("0", "-5")) {
            BusinessRuleException refused = assertThrows(BusinessRuleException.class, () ->
                    service.edit(1L, new UpdateProductRequest("Riz blanc", new BigDecimal(price), "111", null, null, null)));
            assertEquals("PRODUCT_PRICE_INVALID", refused.code());
        }
        assertEquals("Riz", rice.getName());
        assertEquals(new BigDecimal("900"), rice.getPrice());
    }

    @Test
    @DisplayName("Le rayon et le prix d'achat se corrigent avec le reste ; sans prix d'achat saisi, le cout moyen reste")
    void editsTheRayonAndThePurchaseCost() {
        Category drinks = Category.builder().id(7L).name("Boissons").build();
        when(categories.findById(7L)).thenReturn(Optional.of(drinks));

        ProductUnitsResponse response = service.edit(1L,
                new UpdateProductRequest("Riz", new BigDecimal("900"), "111", 7L, new BigDecimal("650"), null));

        assertEquals(drinks, rice.getCategory());
        assertEquals(new BigDecimal("650.000000"), rice.getAverageCost());
        assertEquals("Boissons", response.category().name());

        service.edit(1L, new UpdateProductRequest("Riz", new BigDecimal("900"), "111", null, null, null));

        assertNull(rice.getCategory());
        assertEquals(new BigDecimal("650.000000"), rice.getAverageCost());
        assertEquals(qty(1750), rice.getStockQuantity());
    }

    @Test
    @DisplayName("Un rayon inconnu est refuse, rien n'est ecrit")
    void refusesAnUnknownRayon() {
        assertThrows(ResourceNotFoundException.class, () ->
                service.edit(1L, new UpdateProductRequest("Riz rose", new BigDecimal("900"), "111", 99L, null, null)));
        assertEquals("Riz", rice.getName());
    }

    @Test
    @DisplayName("Un produit inconnu repond introuvable")
    void unknownProduct() {
        assertThrows(ResourceNotFoundException.class, () ->
                service.edit(99L, new UpdateProductRequest("Riz", new BigDecimal("900"), null, null, null, null)));
    }

    @Test
    @DisplayName("Le prix d'achat se saisit dans l'unite d'achat : une tonne a 2 750 000 Ar se relit a l'ariary")
    void purchaseCostTypedPerTonne() {
        ProductPackaging tonne = ProductPackaging.builder().id(40L).product(rice).label("tonne")
                .factor(new BigDecimal("3500.000")).price(new BigDecimal("3000000")).build();
        when(packagings.findById(40L)).thenReturn(Optional.of(tonne));

        ProductUnitsResponse response = service.edit(1L, new UpdateProductRequest("Riz", new BigDecimal("900"),
                "111", null, new BigDecimal("2750000"), 40L));

        assertEquals(tonne, rice.getPurchaseUnit());
        assertEquals(40L, response.purchaseUnitId());
        assertEquals(new BigDecimal("785.714286"), rice.getAverageCost());
        assertEquals(new BigDecimal("2750000.00"), PurchaseCosting.costOfSale(rice, tonne.getFactor()),
                "the tonne reads back at the price typed");
    }

    @Test
    @DisplayName("Une unite d'achat d'un autre produit est refusee, rien n'est ecrit")
    void refusesAnotherProductsUnit() {
        Product sugar = Product.builder().id(2L).name("Sucre").build();
        when(packagings.findById(41L)).thenReturn(Optional.of(ProductPackaging.builder().id(41L)
                .product(sugar).label("sac").factor(new BigDecimal("50.000")).build()));

        BusinessRuleException refused = assertThrows(BusinessRuleException.class, () -> service.edit(1L,
                new UpdateProductRequest("Riz", new BigDecimal("900"), "111", null, new BigDecimal("10"), 41L)));

        assertEquals("PURCHASE_UNIT_UNKNOWN", refused.code());
        assertNull(rice.getAverageCost());
    }
}
