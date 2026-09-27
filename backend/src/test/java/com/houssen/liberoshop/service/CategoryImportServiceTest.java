package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building a rayon tree from a file.
 *
 * <p>The case that drives the design is the one in the middle: a spreadsheet of rayons is sorted
 * alphabetically, so a child routinely appears before its parent. Creating the lines in file order
 * would quietly make those children roots -- a tree that is wrong with nothing on screen to say
 * so, and that has to be repaired one drag at a time.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryImportServiceTest {

    @Mock
    private CategoryRepository categories;
    @Mock
    private ProductRepository products;

    private CategoryImportService service;

    private final List<Category> table = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        CategoryService categoryService = new CategoryService(categories, products);
        service = new CategoryImportService(categories, categoryService);

        Mockito.when(categories.findAllWithParent()).thenAnswer(call -> List.copyOf(table));
        Mockito.when(categories.findAll()).thenAnswer(call -> List.copyOf(table));
        Mockito.when(categories.findById(ArgumentMatchers.anyLong()))
                .thenAnswer(call -> table.stream()
                        .filter(node -> node.getId().equals(call.getArgument(0)))
                        .findFirst());
        Mockito.when(categories.countByParentId(ArgumentMatchers.anyLong()))
                .thenAnswer(call -> table.stream()
                        .filter(node -> node.getParent() != null
                                && node.getParent().getId().equals(call.getArgument(0)))
                        .count());
        Mockito.when(categories.save(ArgumentMatchers.any(Category.class))).thenAnswer(call -> {
            Category saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(nextId.getAndIncrement());
            }
            table.removeIf(node -> node.getId().equals(saved.getId()));
            table.add(saved);
            return saved;
        });
    }

    private Category rayon(String name, Category parent) {
        return categories.save(Category.builder().name(name).parent(parent).build());
    }

    /** What the dialog sends back for every ticked line, unchanged. */
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

    private List<String> paths() {
        return table.stream().map(CategoryService::pathOf).sorted().toList();
    }

    // -------------------------------------------------------------------- preview

    @Test
    @DisplayName("refuses a file with no column that could be a rayon, listing what it read")
    void refusesAFileWithoutNames() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.preview("couleur;taille\nrouge;L\n"));

        assertEquals("IMPORT_NO_NAME_COLUMN", refused.code());
        assertTrue(refused.getMessage().contains("couleur"));
    }

    @Test
    @DisplayName("reads a two-column file as rayon and parent")
    void readsTheParentColumn() {
        rayon("Boissons", null);

        SimpleImportPreviewResponse preview =
                service.preview("rayon;categorie parente\nEau;Boissons\n");

        assertEquals("Eau", value(preview, 0, CategoryImportService.FIELD_NAME));
        assertEquals("Boissons", value(preview, 0, CategoryImportService.FIELD_PARENT));
        assertEquals(1, preview.created());
        assertTrue(preview.missing().isEmpty());
    }

    @Test
    @DisplayName("reads a one-column file of paths, splitting off the parent itself")
    void readsAPathColumn() {
        rayon("Boissons", null);

        SimpleImportPreviewResponse preview = service.preview("categorie\nBoissons > Eau\n");

        assertEquals("Eau", value(preview, 0, CategoryImportService.FIELD_NAME));
        assertEquals("Boissons", value(preview, 0, CategoryImportService.FIELD_PARENT));
        assertEquals(1, preview.created());
        assertTrue(preview.missing().contains("parent"));
    }

    @Test
    @DisplayName("refuses a parent nothing defines, in the preview and not only on applying")
    void previewRefusesAnOrphan() {
        SimpleImportPreviewResponse preview = service.preview("categorie;parent\nEau;Surgeles\n");

        assertEquals(ImportOutcome.SKIPPED, preview.lines().getFirst().outcome());
        assertFalse(preview.lines().getFirst().selected());
        assertTrue(preview.lines().getFirst().notes().stream()
                .anyMatch(note -> note.contains("Surgeles")));
    }

    @Test
    @DisplayName("accepts a parent defined further down the file, since order is not meaning")
    void previewAcceptsAParentDefinedLater() {
        SimpleImportPreviewResponse preview =
                service.preview("categorie;parent\nEau;Boissons\nBoissons;\n");

        assertEquals(2, preview.created());
        assertTrue(preview.lines().getFirst().selected());
    }

    @Test
    @DisplayName("warns that only the last level of a deep path is read as the parent")
    void warnsAboutMiddleLevelsOfAPath() {
        rayon("Eau", null);

        SimpleImportPreviewResponse preview =
                service.preview("categorie\nBoissons > Eau > Eau gazeuse\n");

        assertEquals("Eau gazeuse", value(preview, 0, CategoryImportService.FIELD_NAME));
        assertEquals("Eau", value(preview, 0, CategoryImportService.FIELD_PARENT));
        assertTrue(preview.lines().getFirst().notes().stream()
                .anyMatch(note -> note.contains("niveaux au-dessus")));
    }

    @Test
    @DisplayName("an explicit parent column beats the parentage written into the path")
    void theParentColumnWins() {
        SimpleImportPreviewResponse preview =
                service.preview("categorie;parent\nAlimentaire > Eau;Boissons\n");

        assertEquals("Eau", value(preview, 0, CategoryImportService.FIELD_NAME));
        assertEquals("Boissons", value(preview, 0, CategoryImportService.FIELD_PARENT));
    }

    @Test
    @DisplayName("a rayon the shop already has arrives unticked, since importing it changes nothing")
    void alreadyThereIsNotTicked() {
        rayon("Boissons", null);

        SimpleImportPreviewResponse preview = service.preview("categorie\nboissons\nEau\n");

        assertFalse(preview.lines().getFirst().selected());
        assertEquals(ImportOutcome.MERGED, preview.lines().getFirst().outcome());
        assertEquals(1, preview.merged());
        assertTrue(preview.lines().get(1).selected());
    }

    @Test
    @DisplayName("names the headers it ignored, so a mislabelled parent column is visible")
    void reportsIgnoredHeaders() {
        SimpleImportPreviewResponse preview =
                service.preview("categorie;parrent;note\nEau;Boissons;a revoir\n");

        assertTrue(preview.ignored().contains("parrent"));
        assertTrue(preview.ignored().contains("note"));
        assertTrue(preview.missing().contains("parent"));
    }

    @Test
    @DisplayName("a line with no rayon is left unticked rather than half-created")
    void skipsNamelessLines() {
        SimpleImportPreviewResponse preview = service.preview("categorie;parent\n;Boissons\nEau;\n");

        assertEquals(ImportOutcome.SKIPPED, preview.lines().getFirst().outcome());
        assertFalse(preview.lines().getFirst().selected());
        assertEquals(1, preview.skipped());
    }

    // ---------------------------------------------------------------------- apply

    @Test
    @DisplayName("creates a parent that a later line of the same file defines")
    void createsParentsBeforeChildrenWhateverTheOrder() {
        SimpleImportPreviewResponse preview = service.preview("""
                categorie;parent
                Eau gazeuse;Eau
                Eau;Boissons
                Boissons;
                """);

        SimpleImportResultResponse result = service.apply(asSent(preview));

        assertEquals(3, result.created());
        assertEquals(0, result.skipped());
        assertEquals(List.of("Boissons", "Boissons > Eau", "Boissons > Eau > Eau gazeuse"), paths());
    }

    @Test
    @DisplayName("reports the lines in file order however the passes reached them")
    void reportsInFileOrder() {
        SimpleImportPreviewResponse preview = service.preview("""
                categorie;parent
                Eau;Boissons
                Boissons;
                """);

        SimpleImportResultResponse result = service.apply(asSent(preview));

        assertEquals(List.of(2, 3), result.lines().stream()
                .map(SimpleImportResultResponse.Line::line).toList());
    }

    @Test
    @DisplayName("refuses a rayon whose parent nothing defines, and says which parent")
    void refusesAnOrphan() {
        // Sent as if the operator had ticked it anyway: the preview leaves that possible, and
        // apply is the line of defence that actually holds.
        SimpleImportResultResponse result = service.apply(new SimpleImportRequest(List.of(
                new SimpleImportRequest.Line(2,
                        Map.of(CategoryImportService.FIELD_NAME, "Eau",
                                CategoryImportService.FIELD_PARENT, "Surgeles"),
                        ImportAction.CREATE, null))));

        assertEquals(0, result.created());
        assertEquals(1, result.skipped());
        assertTrue(result.lines().getFirst().note().contains("Surgeles"));
        assertTrue(table.isEmpty(), "an orphan must not quietly become a root");
    }

    @Test
    @DisplayName("hangs a rayon off a parent the shop already has")
    void usesAnExistingParent() {
        Category boissons = rayon("Boissons", null);

        service.apply(asSent(service.preview("categorie;parent\nEau;Boissons\n")));

        assertEquals(2, table.size());
        assertEquals("Boissons > Eau", CategoryService.pathOf(table.getLast()));
        assertEquals(boissons.getId(), table.getLast().getParent().getId());
    }

    @Test
    @DisplayName("says a rayon was already there rather than counting it as created")
    void doesNotRecreateWhatExists() {
        rayon("Boissons", null);

        // The operator ticked it anyway -- the preview leaves that possible.
        SimpleImportResultResponse result = service.apply(new SimpleImportRequest(List.of(
                new SimpleImportRequest.Line(2,
                        Map.of(CategoryImportService.FIELD_NAME, "Boissons",
                                CategoryImportService.FIELD_PARENT, ""),
                        ImportAction.CREATE, null))));

        assertEquals(0, result.created());
        assertEquals(1, result.merged());
        assertEquals(1, table.size());
    }

    @Test
    @DisplayName("refuses one line that would go too deep without failing the others")
    void refusesTooDeepAlone() {
        Category level = null;
        for (int i = 0; i < CategoryService.MAX_DEPTH; i++) {
            level = rayon("Niveau " + i, level);
        }

        SimpleImportResultResponse result = service.apply(new SimpleImportRequest(List.of(
                new SimpleImportRequest.Line(2,
                        Map.of(CategoryImportService.FIELD_NAME, "Trop profond",
                                CategoryImportService.FIELD_PARENT,
                                "Niveau " + (CategoryService.MAX_DEPTH - 1)),
                        ImportAction.CREATE, null),
                new SimpleImportRequest.Line(3,
                        Map.of(CategoryImportService.FIELD_NAME, "Correcte",
                                CategoryImportService.FIELD_PARENT, ""),
                        ImportAction.CREATE, null))));

        assertEquals(1, result.created());
        assertEquals(1, result.skipped());
        assertTrue(result.lines().getFirst().note().contains("niveaux"));
    }
}
