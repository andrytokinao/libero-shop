package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.SimpleImportPreviewResponse;
import com.houssen.liberoshop.web.dto.SimpleImportRequest;
import com.houssen.liberoshop.web.dto.SimpleImportResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading the shop's list of wholesalers out of a file.
 *
 * <p>One rule carries most of these: the shop's own record wins. A contact somebody corrected by
 * hand must survive an export that still holds the old number, because the export is the stale
 * copy and the correction was deliberate. Filling a blank is help; overwriting is damage, and it
 * is silent -- nobody checks a phone number until they need it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierImportServiceTest {

    @Mock
    private SupplierRepository suppliers;

    private SupplierImportService service;

    private final List<Supplier> book = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        service = new SupplierImportService(suppliers);

        Mockito.when(suppliers.findAllByOrderByNameAsc()).thenAnswer(call -> book.stream()
                .sorted(Comparator.comparing(Supplier::getName))
                .toList());
        Mockito.when(suppliers.save(ArgumentMatchers.any(Supplier.class))).thenAnswer(call -> {
            Supplier saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(nextId.getAndIncrement());
            }
            book.removeIf(other -> other.getId().equals(saved.getId()));
            book.add(saved);
            return saved;
        });
    }

    private Supplier known(String name, String contact, String products) {
        Supplier supplier = Supplier.builder()
                .name(name).contact(contact).suppliedProducts(products).build();
        supplier.setId(nextId.getAndIncrement());
        book.add(supplier);
        return supplier;
    }

    private static SimpleImportRequest asSent(SimpleImportPreviewResponse preview) {
        return new SimpleImportRequest(preview.lines().stream()
                .filter(SimpleImportPreviewResponse.Line::selected)
                .map(line -> new SimpleImportRequest.Line(line.line(), line.values(),
                        line.action(), line.existingId()))
                .toList());
    }

    private static String value(SimpleImportPreviewResponse preview, int index, String key) {
        return preview.lines().get(index).values().get(key);
    }

    // -------------------------------------------------------------------- preview

    @Test
    @DisplayName("refuses a file with no column that could be a supplier name")
    void refusesAFileWithoutNames() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.preview("ville;note\nAnalakely;rien\n"));

        assertEquals("IMPORT_NO_NAME_COLUMN", refused.code());
    }

    @Test
    @DisplayName("finds the three columns under the headers a contact book actually uses")
    void mapsColumnsByAnySpelling() {
        SimpleImportPreviewResponse preview = service.preview(
                "Raison sociale,Telephone,Articles\nGrossiste Analakely,034 12 345 67,Riz et sucre\n");

        assertEquals("Grossiste Analakely", value(preview, 0, SupplierImportService.FIELD_NAME));
        assertEquals("034 12 345 67", value(preview, 0, SupplierImportService.FIELD_CONTACT));
        assertEquals("Riz et sucre", value(preview, 0, SupplierImportService.FIELD_PRODUCTS));
        assertTrue(preview.missing().isEmpty());
        assertEquals(1, preview.created());
    }

    @Test
    @DisplayName("warns about a supplier with no contact: the depot would have no way to call")
    void warnsAboutAMissingContact() {
        SimpleImportPreviewResponse preview = service.preview("nom\nGrossiste Analakely\n");

        assertTrue(preview.missing().contains("contact"));
        assertTrue(preview.lines().getFirst().notes().stream()
                .anyMatch(note -> note.contains("joindre")));
    }

    @Test
    @DisplayName("says in advance which fields a merge would fill and which it would leave alone")
    void describesWhatAMergeWouldDo() {
        known("Aqua Plus", "032 55 987 21", null);

        SimpleImportPreviewResponse preview = service.preview(
                "nom;contact;produits\nAqua Plus;038 00 000 00;Eau et jus\n");

        String note = String.join(" ", preview.lines().getFirst().notes());
        assertEquals(ImportOutcome.MERGED, preview.lines().getFirst().outcome());
        assertTrue(note.contains("Sera complete : produits fournis"), note);
        assertTrue(note.contains("Conserve") && note.contains("contact"), note);
    }

    @Test
    @DisplayName("catches a wholesaler written twice in one file")
    void catchesDuplicateLines() {
        SimpleImportPreviewResponse preview = service.preview(
                "nom;contact\nAqua Plus;033 44 221 09\nAQUA  PLUS;038 00 000 00\n");

        assertEquals(1, preview.created());
        assertEquals(1, preview.skipped());
        assertFalse(preview.lines().get(1).selected());
        assertTrue(preview.lines().get(1).notes().stream()
                .anyMatch(note -> note.contains("ligne 2")));
    }

    @Test
    @DisplayName("a line with no name is left unticked")
    void skipsNamelessLines() {
        SimpleImportPreviewResponse preview = service.preview("nom;contact\n;034 12 345 67\n");

        assertEquals(ImportOutcome.SKIPPED, preview.lines().getFirst().outcome());
        assertFalse(preview.lines().getFirst().selected());
    }

    // ---------------------------------------------------------------------- apply

    @Test
    @DisplayName("adds the wholesalers the shop did not have")
    void createsNewSuppliers() {
        SimpleImportPreviewResponse preview = service.preview(
                "nom;contact;produits fournis\nAqua Plus;033 44 221 09;Eau, jus\n");

        SimpleImportResultResponse result = service.apply(asSent(preview));

        assertEquals(1, result.created());
        assertEquals("Aqua Plus", book.getFirst().getName());
        assertEquals("033 44 221 09", book.getFirst().getContact());
        assertEquals("Eau, jus", book.getFirst().getSuppliedProducts());
    }

    @Test
    @DisplayName("fills a blank field but never overwrites one the shop has already typed")
    void mergeFillsBlanksOnly() {
        Supplier aqua = known("Aqua Plus", "032 55 987 21", null);

        service.apply(asSent(service.preview(
                "nom;contact;produits\nAqua Plus;038 00 000 00;Eau et jus\n")));

        assertEquals("032 55 987 21", aqua.getContact(), "a corrected number is the shop's own");
        assertEquals("Eau et jus", aqua.getSuppliedProducts(), "a blank is free to fill");
        assertEquals(1, book.size(), "no second row for the same wholesaler");
    }

    @Test
    @DisplayName("says plainly when a merge had nothing left to fill")
    void reportsANoOpMerge() {
        known("Aqua Plus", "032 55 987 21", "Eau");

        SimpleImportResultResponse result = service.apply(asSent(service.preview(
                "nom;contact;produits\nAqua Plus;038 00 000 00;Jus\n")));

        assertEquals(1, result.merged());
        assertEquals(0, result.created());
        assertTrue(result.lines().getFirst().note().contains("rien a completer"));
    }

    @Test
    @DisplayName("matches by name at apply time, so a colleague's row is not duplicated")
    void rematchesAtApplyTime() {
        SimpleImportPreviewResponse preview =
                service.preview("nom;contact\nAqua Plus;033 44 221 09\n");
        // Created by somebody else between the preview and the apply.
        known("Aqua Plus", null, null);

        SimpleImportResultResponse result = service.apply(asSent(preview));

        assertEquals(0, result.created());
        assertEquals(1, result.merged());
        assertEquals(1, book.size());
        assertEquals("033 44 221 09", book.getFirst().getContact());
    }

    @Test
    @DisplayName("stores a blank optional field as null rather than as an empty string")
    void storesBlanksAsNull() {
        service.apply(new SimpleImportRequest(List.of(
                new SimpleImportRequest.Line(2,
                        Map.of(SupplierImportService.FIELD_NAME, "Aqua Plus",
                                SupplierImportService.FIELD_CONTACT, "  ",
                                SupplierImportService.FIELD_PRODUCTS, ""),
                        ImportAction.CREATE, null))));

        assertNull(book.getFirst().getContact(), "the screens render null as a dash");
        assertNull(book.getFirst().getSuppliedProducts());
    }

    @Test
    @DisplayName("refuses a nameless line without failing the rest of the file")
    void refusesNamelessLinesOnApply() {
        SimpleImportResultResponse result = service.apply(new SimpleImportRequest(List.of(
                new SimpleImportRequest.Line(2,
                        Map.of(SupplierImportService.FIELD_NAME, " "),
                        ImportAction.CREATE, null),
                new SimpleImportRequest.Line(3,
                        Map.of(SupplierImportService.FIELD_NAME, "Aqua Plus"),
                        ImportAction.CREATE, null))));

        assertEquals(1, result.skipped());
        assertEquals(1, result.created());
    }
}
