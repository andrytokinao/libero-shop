package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A wholesaler added from the receipt form. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierServiceTest {

    @Mock
    private SupplierRepository suppliers;

    private SupplierService service;

    private final List<Supplier> book = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new SupplierService(suppliers);
        Mockito.when(suppliers.findAllByOrderByNameAsc()).thenAnswer(call -> List.copyOf(book));
        Mockito.when(suppliers.findById(ArgumentMatchers.anyLong())).thenAnswer(call -> book.stream()
                .filter(s -> s.getId().equals(call.getArgument(0))).findFirst());
        Mockito.when(suppliers.save(ArgumentMatchers.any(Supplier.class))).thenAnswer(call -> {
            Supplier saved = call.getArgument(0);
            saved.setId((long) book.size() + 1);
            book.add(saved);
            return saved;
        });
    }

    @Test
    void nouveauFournisseurEnregistreAvecSonNomNettoye() {
        SupplierService.CreatedSupplier created = service.create("  Distri   Lait  ", " 034 11 222 33 ");

        assertEquals("Distri Lait", created.name());
        assertEquals("034 11 222 33", created.contact());
        assertEquals(1, book.size());
    }

    @Test
    void contactVideEnregistreCommeAbsent() {
        assertNull(service.create("SOCOLAIT", "  ").contact());
    }

    @Test
    void memeNomEcritAutrementRefuse() {
        book.add(Supplier.builder().id(7L).name("Grossiste Analakely").build());

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.create("grossiste  analakély", null));

        assertEquals("SUPPLIER_EXISTS", refused.code());
        assertTrue(refused.getMessage().contains("Grossiste Analakely"));
        assertEquals(1, book.size());
    }

    @Test
    void nomVideRefuse() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.create("   ", "034"));

        assertEquals("SUPPLIER_NAME_REQUIRED", refused.code());
    }

    @Test
    void fournisseurCorrigeGardeSonNomSansSeHeurterALuiMeme() {
        book.add(Supplier.builder().id(7L).name("Grossiste Analakely").build());

        SupplierService.CreatedSupplier updated = service.update(7L, "Grossiste  analakely", "034 00 000 01");

        assertEquals("Grossiste analakely", updated.name());
        assertEquals("034 00 000 01", updated.contact());
    }

    @Test
    void fournisseurCorrigeSurLeNomDUnAutreRefuse() {
        book.add(Supplier.builder().id(7L).name("Grossiste Analakely").build());
        book.add(Supplier.builder().id(8L).name("SOCOLAIT").build());

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.update(8L, "grossiste analakely", null));
        assertEquals("SUPPLIER_EXISTS", refused.code());
    }

    @Test
    void fournisseurNePlusProposerPuisReproposer() {
        book.add(Supplier.builder().id(7L).name("SOCOLAIT").build());

        assertTrue(service.changeRetired(7L, true).retired());
        assertFalse(service.changeRetired(7L, false).retired());
    }
}
