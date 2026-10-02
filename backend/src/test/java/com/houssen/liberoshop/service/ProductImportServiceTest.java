package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.ProductImportLineRequest;
import com.houssen.liberoshop.web.dto.ProductImportLineResponse;
import com.houssen.liberoshop.web.dto.ProductImportPreviewResponse;
import com.houssen.liberoshop.web.dto.ProductImportRequest;
import com.houssen.liberoshop.web.dto.ProductImportResultResponse;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.houssen.liberoshop.util.QuantityAssertions.assertQuantity;
import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a shop's own export file does to its catalogue.
 *
 * <p>The cases below are the ones that cost a catalogue if they go wrong, and none of them fails
 * loudly: a file loaded twice doubling the stock, three hundred duplicate references because a
 * name was matched too strictly, a barcode landing on the wrong product, a price read as 1.5
 * Ariary because the spreadsheet wrote "1.500". The preview exists so an operator can see these;
 * these tests exist so the preview is worth looking at.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductImportServiceTest {

    @Mock
    private ProductRepository products;
    @Mock
    private CategoryRepository categories;
    @Mock
    private SupplierRepository suppliers;
    @Mock
    private StockMovementRepository movements;
    @Mock
    private BusinessCalendar calendar;

    private ProductImportService service;

    private final List<Product> shelf = new ArrayList<>();
    private final List<Category> rayons = new ArrayList<>();
    private final List<Supplier> wholesalers = new ArrayList<>();
    /** Every stock entry the import wrote, in order. */
    private final List<Supply> ledger = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    /** Who is signed in. The actor never travels in the payload, so it is passed to apply. */
    private final UserApp operator = UserApp.builder()
            .id(99L)
            .fullName("Nadia Rasolofo")
            .username("nadia")
            .enabled(true)
            .build();

    @BeforeEach
    void setUp() {
        // A real CategoryService on the same mocked table: the path handling is half of what
        // an import does with a category column, and stubbing it would assert the stub.
        CategoryService categoryService = new CategoryService(categories, products);
        service = new ProductImportService(products, categories, suppliers, movements,
                categoryService, calendar, event -> { });

        Mockito.when(calendar.now()).thenReturn(LocalDateTime.of(2026, 9, 26, 9, 0));
        Mockito.when(movements.save(ArgumentMatchers.any(Supply.class))).thenAnswer(call -> {
            Supply entry = call.getArgument(0);
            entry.setId(nextId.getAndIncrement());
            ledger.add(entry);
            return entry;
        });
        Mockito.when(suppliers.findById(ArgumentMatchers.anyLong()))
                .thenAnswer(call -> wholesalers.stream()
                        .filter(supplier -> supplier.getId().equals(call.getArgument(0)))
                        .findFirst());

        Mockito.when(products.findAllWithCategory()).thenAnswer(call -> List.copyOf(shelf));
        Mockito.when(products.findByIdForUpdate(ArgumentMatchers.anyLong()))
                .thenAnswer(call -> shelf.stream()
                        .filter(product -> product.getId().equals(call.getArgument(0)))
                        .findFirst());
        Mockito.when(products.save(ArgumentMatchers.any(Product.class))).thenAnswer(call -> {
            Product saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(nextId.getAndIncrement());
            }
            shelf.add(saved);
            return saved;
        });

        Mockito.when(categories.findAllWithParent()).thenAnswer(call -> List.copyOf(rayons));
        Mockito.when(categories.findAll()).thenAnswer(call -> List.copyOf(rayons));
        Mockito.when(categories.findById(ArgumentMatchers.anyLong()))
                .thenAnswer(call -> rayons.stream()
                        .filter(node -> node.getId().equals(call.getArgument(0)))
                        .findFirst());
        Mockito.when(categories.save(ArgumentMatchers.any(Category.class))).thenAnswer(call -> {
            Category saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(nextId.getAndIncrement());
            }
            rayons.add(saved);
            return saved;
        });
    }

    // ------------------------------------------------------------------- fixtures

    private Category rayon(String name, Category parent) {
        return categories.save(Category.builder().name(name).parent(parent).build());
    }

    private Product inCatalogue(String name, int stock, String barcode, Category rayon) {
        Product product = Product.builder()
                .name(name)
                .price(BigDecimal.valueOf(12500))
                .stockQuantity(qty(stock))
                .barcode(barcode)
                .category(rayon)
                .build();
        product.setId(nextId.getAndIncrement());
        shelf.add(product);
        return product;
    }

    private Supplier wholesaler(String name) {
        Supplier supplier = Supplier.builder().name(name).build();
        supplier.setId(nextId.getAndIncrement());
        wholesalers.add(supplier);
        return supplier;
    }

    private static ProductImportLineResponse line(ProductImportPreviewResponse preview, int index) {
        return preview.lines().get(index);
    }

    /** An inventory count: nobody delivered these goods, so no supplier is named. */
    private static ProductImportRequest request(List<ProductImportLineRequest> lines) {
        return new ProductImportRequest(lines, null);
    }

    private ProductImportResultResponse run(ProductImportRequest request) {
        return service.apply(request, operator);
    }

    /** The request a browser would send back for one previewed line, unchanged. */
    private static ProductImportLineRequest asSent(ProductImportLineResponse line) {
        return new ProductImportLineRequest(
                line.line(),
                line.suggestedName() != null && line.action() == ImportAction.CREATE
                        && line.outcome() == ImportOutcome.RENAMED
                        ? line.suggestedName() : line.name(),
                line.quantity(),
                line.unit(),
                line.price() == null ? BigDecimal.ZERO : line.price(),
                line.cost(),
                line.barcode(),
                line.categoryId(),
                line.categoryPath(),
                line.action(),
                line.existing() == null ? null : line.existing().id());
    }

    // -------------------------------------------------------------------- reading

    @Test
    @DisplayName("refuses a file with no column that could be a product name, listing what it read")
    void refusesAFileWithoutNames() {
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.preview("quantite;prix\n40;12500\n"));

        assertEquals("IMPORT_NO_NAME_COLUMN", refused.code());
        assertTrue(refused.getMessage().contains("quantite"), "the headers read must be shown");
    }

    @Test
    @DisplayName("finds the recommended columns under the headers the shop actually wrote")
    void mapsColumnsByAnySpelling() {
        ProductImportPreviewResponse preview = service.preview(
                "Désignation;Qte stock;Unité;Prix de vente;Coût unitaire;Code-Barres;Rayon\n"
                        + "Riz parfumé 5kg;40;sac;12 500 Ar;10 200 Ar;6001001000015;Alimentaire\n");

        ProductImportLineResponse row = line(preview, 0);
        assertEquals("Riz parfumé 5kg", row.name());
        assertQuantity(40, row.quantity());
        assertEquals("sac", row.unit());
        assertEquals(0, new BigDecimal("12500.00").compareTo(row.price()));
        assertEquals(0, new BigDecimal("10200.00").compareTo(row.cost()));
        assertEquals("6001001000015", row.barcode());
        assertEquals("Alimentaire", row.categoryPath());
        assertTrue(preview.missing().isEmpty(), "all seven columns were found");
    }

    @Test
    @DisplayName("names the columns it could not find and the headers it ignored")
    void reportsWhatItCouldNotUse() {
        ProductImportPreviewResponse preview = service.preview(
                "nom;quantit;fournisseur\nRiz 5kg;40;Grossiste\n");

        assertTrue(preview.missing().contains("prix"));
        assertTrue(preview.missing().contains("quantite"),
                "a misspelled quantity column must be reported, not read as zero");
        assertTrue(preview.ignored().contains("quantit"));
        assertTrue(preview.ignored().contains("fournisseur"));
        assertQuantity(0, line(preview, 0).quantity());
    }

    @Test
    @DisplayName("reads a French spreadsheet's numbers: grouped thousands, comma decimals, currency")
    void readsFrenchNumbers() {
        assertEquals(0, new BigDecimal("12500").compareTo(ProductImportColumns.number("12 500 Ar")));
        assertEquals(0, new BigDecimal("12500").compareTo(ProductImportColumns.number("12.500")));
        assertEquals(0, new BigDecimal("12500.75").compareTo(ProductImportColumns.number("12.500,75")));
        assertEquals(0, new BigDecimal("12500.75").compareTo(ProductImportColumns.number("12,500.75")));
        assertEquals(0, new BigDecimal("2.5").compareTo(ProductImportColumns.number("2,5")));
        assertEquals(0, new BigDecimal("2.50").compareTo(ProductImportColumns.number("2.50")));
        assertNull(ProductImportColumns.number("a completer"));
        assertNull(ProductImportColumns.number("1.2.3"));
        assertNull(ProductImportColumns.number(null));
    }

    @Test
    @DisplayName("keeps a weighed quantity, and says so when it has to round past the thousandth")
    void notesARoundedQuantity() {
        ProductImportPreviewResponse preview =
                service.preview("nom;quantite\nFarine en vrac;12,4\nSucre en vrac;3,14159\n");

        assertQuantity("12.4", line(preview, 0).quantity());
        assertTrue(line(preview, 0).notes().stream().noneMatch(note -> note.contains("arrondie")));
        assertQuantity("3.142", line(preview, 1).quantity());
        assertTrue(line(preview, 1).notes().stream().anyMatch(note -> note.contains("arrondie")));
    }

    @Test
    @DisplayName("reads a negative quantity as zero and says why: an import adds stock")
    void refusesNegativeQuantities() {
        ProductImportPreviewResponse preview = service.preview("nom;quantite\nRiz;-5\n");

        assertQuantity(0, line(preview, 0).quantity());
        assertTrue(line(preview, 0).notes().stream().anyMatch(note -> note.contains("negative")));
    }

    @Test
    @DisplayName("a line with no product name is left unticked rather than half-imported")
    void skipsNamelessLines() {
        ProductImportPreviewResponse preview =
                service.preview("nom;quantite\n;40\nRiz 5kg;10\n");

        assertEquals(ImportOutcome.SKIPPED, line(preview, 0).outcome());
        assertFalse(line(preview, 0).selected());
        assertEquals(ImportOutcome.CREATED, line(preview, 1).outcome());
        assertEquals(1, preview.skipped());
    }

    @Test
    @DisplayName("asks for a price the file does not carry instead of importing goods at zero")
    void flagsAMissingPrice() {
        ProductImportPreviewResponse preview = service.preview("nom;quantite\nRiz 5kg;40\n");

        assertNull(line(preview, 0).price());
        assertTrue(line(preview, 0).notes().stream().anyMatch(note -> note.contains("prix")));
    }

    // ------------------------------------------------------- matching the catalogue

    @Test
    @DisplayName("a product nobody has is a creation")
    void newProductIsACreation() {
        ProductImportPreviewResponse preview = service.preview("nom;quantite;prix\nSel 1kg;30;900\n");

        assertEquals(ImportOutcome.CREATED, line(preview, 0).outcome());
        assertEquals(ImportAction.CREATE, line(preview, 0).action());
        assertNull(line(preview, 0).existing());
        assertEquals(1, preview.created());
    }

    @Test
    @DisplayName("an existing name defaults to adding to that product's stock, and shows it")
    void existingNameMergesByDefault() {
        Product riz = inCatalogue("Riz 5kg", 40, "6001001000015", null);

        ProductImportPreviewResponse preview = service.preview("nom;quantite;prix\nriz 5 KG;12;12500\n");

        ProductImportLineResponse row = line(preview, 0);
        assertEquals(ImportOutcome.MERGED, row.outcome());
        assertEquals(ImportAction.MERGE, row.action());
        assertEquals("name", row.matchedOn());
        assertEquals(riz.getId(), row.existing().id());
        assertQuantity(40, row.existing().stockQuantity(), "the row must be able to show 40 + 12");
        assertEquals(1, preview.merged());
    }

    @Test
    @DisplayName("a barcode match wins over the name, and says it was matched on the barcode")
    void barcodeMatchWins() {
        Product riz = inCatalogue("Riz parfume 5kg", 40, "6001001000015", null);

        ProductImportPreviewResponse preview = service.preview(
                "nom;quantite;prix;code barres\nRiz de luxe;12;12500;6001001000015\n");

        ProductImportLineResponse row = line(preview, 0);
        assertEquals(ImportOutcome.MERGED, row.outcome());
        assertEquals("barcode", row.matchedOn());
        assertEquals(riz.getId(), row.existing().id());
    }

    @Test
    @DisplayName("same name but another barcode is another product, proposed under a free name")
    void sameNameDifferentBarcodeIsARename() {
        inCatalogue("Lait 1L", 15, "6001002000053", null);

        ProductImportPreviewResponse preview = service.preview(
                "nom;quantite;prix;code barres\nLait 1L;20;4800;6009999999999\n");

        ProductImportLineResponse row = line(preview, 0);
        assertEquals(ImportOutcome.RENAMED, row.outcome());
        assertEquals(ImportAction.CREATE, row.action());
        assertEquals("Lait 1L (2)", row.suggestedName());
        assertNotNull(row.existing(), "the operator has to see which product it looked like");
        assertTrue(row.notes().stream().anyMatch(note -> note.contains("code-barres")));
        assertEquals(1, preview.renamed());
    }

    @Test
    @DisplayName("every merge row offers a free name, since renaming is a per-row decision")
    void mergeRowsAlsoCarryAFreeName() {
        inCatalogue("Riz 5kg", 40, null, null);

        ProductImportPreviewResponse preview = service.preview("nom;quantite;prix\nRiz 5kg;12;12500\n");

        assertEquals(ImportAction.MERGE, line(preview, 0).action());
        assertEquals("Riz 5kg (2)", line(preview, 0).suggestedName());
    }

    @Test
    @DisplayName("two lines of the same file for one product are added up, once")
    void foldsDuplicateLines() {
        ProductImportPreviewResponse preview = service.preview(
                "nom;quantite;prix\nSel 1kg;30;900\nAutre;5;100\nSEL 1KG;20;900\n");

        assertQuantity(50, line(preview, 0).quantity(), "the two lines' quantities add up");
        assertTrue(line(preview, 0).notes().stream().anyMatch(note -> note.contains("ligne 4")));
        assertEquals(ImportOutcome.SKIPPED, line(preview, 2).outcome());
        assertFalse(line(preview, 2).selected());
        assertTrue(line(preview, 2).notes().stream().anyMatch(note -> note.contains("Doublon")));
    }

    @Test
    @DisplayName("a repeated barcode folds the lines even when the wording differs")
    void foldsOnBarcodeToo() {
        ProductImportPreviewResponse preview = service.preview(
                "nom;quantite;prix;ean\nEau 1.5L;10;1200;600400\nEau minerale 1,5 L;6;1200;600400\n");

        assertQuantity(16, line(preview, 0).quantity());
        assertEquals(ImportOutcome.SKIPPED, line(preview, 1).outcome());
    }

    @Test
    @DisplayName("two new products of the same name do not both get created")
    void doesNotCreateTwoReferencesForOneName() {
        ProductImportPreviewResponse preview = service.preview(
                "nom;quantite;prix\nSel 1kg;30;900\nSel 1kg;30;900\n");

        assertEquals(1, preview.created());
        assertEquals(1, preview.skipped());
    }

    // ------------------------------------------------------------------ categories

    @Test
    @DisplayName("resolves a rayon the shop already has, by the last segment of the path")
    void resolvesAKnownRayon() {
        Category boissons = rayon("Boissons", null);
        Category eau = rayon("Eau", boissons);

        ProductImportPreviewResponse preview = service.preview(
                "nom;prix;categorie\nEau 1.5L;1200;Boissons > Eau\n");

        assertEquals(eau.getId(), line(preview, 0).categoryId());
        assertTrue(preview.createdRayons().isEmpty());
        assertEquals(0, preview.withoutCategory());
    }

    @Test
    @DisplayName("announces the rayons the file would create, because a typo grows one for good")
    void announcesRayonsToCreate() {
        rayon("Boissons", null);

        ProductImportPreviewResponse preview = service.preview(
                "nom;prix;categorie\nEau 1.5L;1200;Boissons > Eau\nCafe;7400;Epicerie\n");

        assertNull(line(preview, 0).categoryId());
        assertEquals(List.of("Boissons > Eau", "Epicerie"), preview.createdRayons());
        assertEquals(0, preview.withoutCategory(), "these lines do have a rayon, just a new one");
    }

    @Test
    @DisplayName("ignores the category column of a merge line, which keeps the shop's own rayon")
    void aMergeNeverGrowsARayon() {
        Category epicerie = rayon("Epicerie", null);
        Product riz = inCatalogue("Riz 5kg", 40, null, epicerie);

        ProductImportPreviewResponse preview = service.preview(
                "nom;quantite;prix;categorie\nRiz 5kg;12;12500;Alimentaire > Cereales\n");
        assertTrue(preview.createdRayons().isEmpty(),
                "announcing a rayon the apply will not create would be a false promise");

        ProductImportResultResponse result =
                run(request(List.of(asSent(line(preview, 0)))));

        assertTrue(result.rayonsCreated().isEmpty());
        assertEquals(1, rayons.size(), "no rayon may have been grown for a line that uses none");
        assertSame(epicerie, riz.getCategory());
    }

    @Test
    @DisplayName("counts the new products with no rayon at all -- what the second dialog asks about")
    void countsLinesNeedingARayon() {
        inCatalogue("Riz 5kg", 40, null, rayon("Alimentaire", null));

        ProductImportPreviewResponse preview = service.preview(
                "nom;prix;categorie\nSel 1kg;900;\nSucre;3200;Alimentaire\nRiz 5kg;12500;\n");

        assertEquals(1, preview.withoutCategory(),
                "only the new product without a rayon; a merge keeps the shop's own");
    }

    // ---------------------------------------------------------------------- apply

    @Test
    @DisplayName("a merge raises the stock and leaves the shop's own name and price alone")
    void mergeRaisesTheStockOnly() {
        Product riz = inCatalogue("Riz 5kg", 40, "6001001000015", null);
        riz.setPrice(BigDecimal.valueOf(12500));

        ProductImportPreviewResponse preview = service.preview("nom;quantite;prix\nRiz 5kg;12;99999\n");
        ProductImportResultResponse result =
                run(request(List.of(asSent(line(preview, 0)))));

        assertEquals(1, result.merged());
        assertQuantity(12, result.unitsAdded());
        assertQuantity(52, riz.getStockQuantity());
        assertEquals("Riz 5kg", riz.getName());
        assertEquals(0, BigDecimal.valueOf(12500).compareTo(riz.getPrice()),
                "the file is a delivery note, not a re-pricing");
    }

    @Test
    @DisplayName("a merge fills in a unit the product never had, and only then")
    void mergeFillsABlankUnit() {
        Product riz = inCatalogue("Riz 5kg", 40, null, null);
        Product huile = inCatalogue("Huile 1L", 10, null, null);
        huile.setUnit("bidon");

        ProductImportPreviewResponse preview =
                service.preview("nom;quantite;prix;unite\nRiz 5kg;12;12500;sac\nHuile 1L;5;6800;carton\n");
        run(request(
                List.of(asSent(line(preview, 0)), asSent(line(preview, 1)))));

        assertEquals("sac", riz.getUnit(), "a blank was filled");
        assertEquals("bidon", huile.getUnit(), "an existing unit is the shop's own");
    }

    @Test
    @DisplayName("creates the reference, its stock and its rayon in one go")
    void createAddsTheReference() {
        ProductImportPreviewResponse preview = service.preview(
                "nom;quantite;prix;unite;categorie\nSel 1kg;30;900;sachet;Epicerie > Condiments\n");

        ProductImportResultResponse result =
                run(request(List.of(asSent(line(preview, 0)))));

        assertEquals(1, result.created());
        assertEquals(List.of("Epicerie > Condiments"), result.rayonsCreated());
        Product created = shelf.getLast();
        assertEquals("Sel 1kg", created.getName());
        assertQuantity(30, created.getStockQuantity());
        assertEquals("sachet", created.getUnit());
        assertEquals("Condiments", created.getCategory().getName());
        assertEquals("Epicerie", created.getCategory().getParent().getName());
    }

    @Test
    @DisplayName("creates each rayon of a file once, however many lines name it")
    void createsEachRayonOnce() {
        ProductImportPreviewResponse preview = service.preview(
                "nom;prix;categorie\nSel;900;Epicerie\nSucre;3200;Epicerie\nPoivre;1200;Epicerie\n");

        ProductImportResultResponse result = run(request(
                preview.lines().stream().map(ProductImportServiceTest::asSent).toList()));

        assertEquals(List.of("Epicerie"), result.rayonsCreated());
        assertEquals(1, rayons.size());
    }

    @Test
    @DisplayName("numbers a name taken since the preview instead of creating a twin of it")
    void freesATakenNameAtApplyTime() {
        ProductImportPreviewResponse preview = service.preview("nom;quantite;prix\nSel 1kg;30;900\n");
        // A colleague creates it between the preview and the apply.
        inCatalogue("Sel 1kg", 5, null, null);

        ProductImportResultResponse result =
                run(request(List.of(asSent(line(preview, 0)))));

        assertEquals(1, result.created());
        assertEquals(ImportOutcome.RENAMED, result.lines().getFirst().outcome());
        assertEquals("Sel 1kg (2)", shelf.getLast().getName());
        assertTrue(result.lines().getFirst().note().contains("Sel 1kg (2)"));
    }

    @Test
    @DisplayName("creates without a barcode rather than stealing one, and says whose it was")
    void doesNotStealABarcode() {
        inCatalogue("Lait 1L", 15, "6001002000053", null);

        ProductImportPreviewResponse preview = service.preview(
                "nom;quantite;prix;code barres\nLait entier 1L;20;4800;6001002000053\n");
        // The preview would have matched on the barcode; the operator insists on a new
        // reference, which is the case this covers.
        ProductImportLineRequest insisted = new ProductImportLineRequest(
                2, "Lait entier 1L", qty(20), null, BigDecimal.valueOf(4800), null, "6001002000053",
                null, null, ImportAction.CREATE, null);

        ProductImportResultResponse result =
                run(request(List.of(insisted)));

        assertEquals(1, result.created());
        assertNull(shelf.getLast().getBarcode());
        assertTrue(result.lines().getFirst().note().contains("Lait 1L"));
        assertNotNull(preview);
    }

    @Test
    @DisplayName("reports a merge target that no longer exists instead of failing the import")
    void reportsAVanishedMergeTarget() {
        ProductImportLineRequest orphan = new ProductImportLineRequest(
                2, "Riz 5kg", qty(12), null, BigDecimal.valueOf(12500), null, null, null, null,
                ImportAction.MERGE, 404L);
        ProductImportLineRequest fine = new ProductImportLineRequest(
                3, "Sel 1kg", qty(30), null, BigDecimal.valueOf(900), null, null, null, null,
                ImportAction.CREATE, null);

        ProductImportResultResponse result =
                run(request(List.of(orphan, fine)));

        assertEquals(1, result.skipped());
        assertEquals(1, result.created(), "one refused line must not fail the other 299");
        assertEquals(ImportOutcome.SKIPPED, result.lines().getFirst().outcome());
        assertNull(result.lines().getFirst().productId());
    }

    // -------------------------------------------------------------------- the ledger

    @Test
    @DisplayName("every line that raises a stock leaves an entry signed by whoever imported")
    void writesAStockEntryPerLine() {
        inCatalogue("Riz 5kg", 40, null, null);

        ProductImportPreviewResponse preview =
                service.preview("nom;quantite;prix\nRiz 5kg;12;12500\nSel 1kg;30;900\n");
        ProductImportResultResponse result = run(request(
                preview.lines().stream().map(ProductImportServiceTest::asSent).toList()));

        assertEquals(2, result.movements());
        assertEquals(2, ledger.size());
        assertQuantity(12, ledger.get(0).getQuantity());
        assertQuantity(30, ledger.get(1).getQuantity());
        assertTrue(ledger.stream().allMatch(entry -> entry.getPerformedBy() == operator),
                "a stock that went up must always name somebody");
        assertTrue(ledger.stream().allMatch(Supply::isFromImport));
        assertNull(result.supplierName());
    }

    @Test
    @DisplayName("names the supplier on every entry when the file came from one")
    void namesTheSupplierWhenThereIsOne() {
        Supplier grossiste = wholesaler("Grossiste Analakely");

        ProductImportPreviewResponse preview = service.preview("nom;quantite;prix\nSel 1kg;30;900\n");
        ProductImportResultResponse result = run(new ProductImportRequest(
                List.of(asSent(line(preview, 0))), grossiste.getId()));

        assertEquals("Grossiste Analakely", result.supplierName());
        assertEquals(1, ledger.size());
        assertSame(grossiste, ledger.getFirst().getSupplier());
        assertFalse(ledger.getFirst().isFromImport());
    }

    @Test
    @DisplayName("refuses the whole import rather than booking it against a supplier that is gone")
    void refusesAnUnknownSupplier() {
        ProductImportPreviewResponse preview = service.preview("nom;quantite;prix\nSel 1kg;30;900\n");
        ProductImportRequest orphaned =
                new ProductImportRequest(List.of(asSent(line(preview, 0))), 404L);

        assertThrows(ResourceNotFoundException.class, () -> run(orphaned));
        assertTrue(ledger.isEmpty());
    }

    @Test
    @DisplayName("a line at quantity zero adds a reference without inventing a movement")
    void doesNotBookAnEmptyEntry() {
        ProductImportPreviewResponse preview = service.preview("nom;quantite;prix\nSel 1kg;0;900\n");
        ProductImportResultResponse result = run(request(List.of(asSent(line(preview, 0)))));

        assertEquals(1, result.created());
        assertEquals(0, result.movements());
        assertTrue(ledger.isEmpty(), "a movement of nothing would be noise in the supplies list");
    }

    @Test
    @DisplayName("stamps one moment on the whole file, not one per line")
    void stampsTheFileOnce() {
        ProductImportPreviewResponse preview =
                service.preview("nom;quantite;prix\nSel 1kg;30;900\nPoivre;8;3200\n");
        run(request(preview.lines().stream().map(ProductImportServiceTest::asSent).toList()));

        assertEquals(1, ledger.stream().map(Supply::getMovementDate).distinct().count(),
                "one act, one timestamp -- otherwise the supplies list reads as a trickle");
    }

    @Test
    @DisplayName("puts a line in the rayon it names by id, in preference to any path")
    void categoryIdWinsOverThePath() {
        Category alimentaire = rayon("Alimentaire", null);

        ProductImportLineRequest sent = new ProductImportLineRequest(
                2, "Sel 1kg", qty(30), null, BigDecimal.valueOf(900), null, null,
                alimentaire.getId(), "Un autre rayon", ImportAction.CREATE, null);

        run(request(List.of(sent)));

        assertSame(alimentaire, shelf.getLast().getCategory());
        assertEquals(1, rayons.size(), "the path must not have created a rayon");
    }

    // --------------------------------------------------------------- purchase cost

    @Test
    @DisplayName("reads a purchase price apart from the sale price, whatever its spelling")
    void readsThePurchasePrice() {
        ProductImportPreviewResponse preview = service.preview(
                "Désignation;Prix de vente;Prix d'achat\nSel 1kg;900;650\n");

        assertEquals(0, BigDecimal.valueOf(900).compareTo(line(preview, 0).price()));
        assertEquals(0, BigDecimal.valueOf(650).compareTo(line(preview, 0).cost()));
    }

    @Test
    @DisplayName("an unreadable purchase price is noted and dropped, never a reason to refuse")
    void unreadableCostIsOptional() {
        ProductImportPreviewResponse preview =
                service.preview("nom;prix;cout\nSel 1kg;900;beaucoup\n");

        assertNull(line(preview, 0).cost());
        assertTrue(line(preview, 0).selected());
        assertTrue(line(preview, 0).notes().stream().anyMatch(note -> note.contains("achat")));
    }

    @Test
    @DisplayName("a new reference starts with its purchase price as average cost")
    void createSeedsTheAverageCost() {
        ProductImportPreviewResponse preview =
                service.preview("nom;quantite;prix;prix achat\nSel 1kg;30;900;650\n");
        run(request(List.of(asSent(line(preview, 0)))));

        Product created = shelf.getLast();
        assertQuantity(30, created.getStockQuantity());
        assertEquals(new BigDecimal("650.00"), created.getAverageCost());
        assertEquals(new BigDecimal("650.00"), ledger.getFirst().getUnitCost());
    }

    @Test
    @DisplayName("a merge blends the file's cost into the average, weighted by the units")
    void mergeBlendsTheAverageCost() {
        Product riz = inCatalogue("Riz 5kg", 40, null, null);
        riz.setAverageCost(new BigDecimal("10000.00"));

        ProductImportPreviewResponse preview =
                service.preview("nom;quantite;prix;prix achat\nRiz 5kg;10;12500;11000\n");
        run(request(List.of(asSent(line(preview, 0)))));

        // (40 × 10 000 + 10 × 11 000) / 50
        assertEquals(new BigDecimal("10200.00"), riz.getAverageCost());
        assertQuantity(50, riz.getStockQuantity());
    }

    @Test
    @DisplayName("a merge without a cost leaves the average alone")
    void mergeWithoutCostKeepsTheAverage() {
        Product riz = inCatalogue("Riz 5kg", 40, null, null);
        riz.setAverageCost(new BigDecimal("10000.00"));

        ProductImportPreviewResponse preview = service.preview("nom;quantite\nRiz 5kg;10\n");
        run(request(List.of(asSent(line(preview, 0)))));

        assertEquals(new BigDecimal("10000.00"), riz.getAverageCost());
        assertNull(ledger.getFirst().getUnitCost());
    }

    @Test
    @DisplayName("two lines of one product at two costs fold into their weighted average")
    void foldedLinesBlendTheirCosts() {
        ProductImportPreviewResponse preview = service.preview(
                "nom;quantite;prix;prix achat\nSel 1kg;10;900;600\nSel 1kg;30;900;700\n");

        assertQuantity(40, line(preview, 0).quantity());
        assertEquals(new BigDecimal("675.00"), line(preview, 0).cost());
    }
}
