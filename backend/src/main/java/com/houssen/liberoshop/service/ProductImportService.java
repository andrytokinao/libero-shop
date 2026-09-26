package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.service.ProductImportColumns.Column;
import com.houssen.liberoshop.service.ProductImportColumns.Mapping;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.ProductImportLineRequest;
import com.houssen.liberoshop.web.dto.ProductImportLineResponse;
import com.houssen.liberoshop.web.dto.ProductImportPreviewResponse;
import com.houssen.liberoshop.web.dto.ProductImportRequest;
import com.houssen.liberoshop.web.dto.ProductImportResultResponse;
import com.houssen.liberoshop.web.dto.ProductRefResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loading a catalogue from a file, in two steps that never blur into one.
 *
 * <p>{@link #preview(String)} reads the file, confronts every line with the catalogue and
 * writes nothing. {@link #apply(ProductImportRequest)} takes back the lines the operator
 * ticked and is the only method here that touches the database. The split is the feature: an
 * import is the one operation in this application that can quietly wreck a catalogue -- three
 * hundred duplicate references, a stock doubled because a file was loaded twice -- and the only
 * defence against that is showing what would happen before it does.
 *
 * <p>Nothing survives between the two calls. The preview is handed to the browser, edited
 * there, and sent back whole; there is no server-side draft, matching the stateless story the
 * rest of the application tells. The price is that every line is matched twice, which is a
 * couple of hash lookups, and the benefit is that a shop can leave the dialog open through
 * lunch without an expiry surprising them.
 *
 * <p><b>A deliberate gap.</b> An import writes no {@code StockMovement}. Merging 12 units into
 * a product raises the stock with nothing in the ledger explaining the rise, where a delivery
 * booked through {@code StockService.registerSupply} leaves a {@code Supply} naming a supplier
 * and an agent. That is on purpose for now: {@code Supply} requires a supplier, and an import
 * is usually an opening stock count rather than a receipt from one wholesaler. It does mean the
 * import must be read as setting the shelf, not as recording a delivery -- and it is why the
 * dialog says so before applying.
 */
@Service
@Transactional(readOnly = true)
public class ProductImportService {

    /**
     * How many numbered variants of a name are tried before giving up. Beyond this the file is
     * not describing new products, it is repeating one.
     */
    private static final int MAX_RENAME_ATTEMPTS = 200;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CategoryService categoryService;

    public ProductImportService(ProductRepository products, CategoryRepository categories,
                                CategoryService categoryService) {
        this.products = products;
        this.categories = categories;
        this.categoryService = categoryService;
    }

    // --------------------------------------------------------------------- preview

    /**
     * Reads the file and says what applying it would do, without doing any of it.
     *
     * @param content the file's text, already decoded by the caller
     * @throws BusinessRuleException when the file has no header, too many rows, or no column
     *                               that could be a product name -- the three cases where there
     *                               is nothing to preview rather than something to correct
     */
    public ProductImportPreviewResponse preview(String content) {
        CsvTable table = CsvTable.parse(content);
        Mapping mapping = ProductImportColumns.map(table);
        if (!mapping.has(Column.NAME)) {
            throw new BusinessRuleException("IMPORT_NO_NAME_COLUMN",
                    "Aucune colonne de nom de produit n'a ete reconnue. Nommez-la \""
                            + Column.NAME.label() + "\" (ou \"designation\", \"article\", "
                            + "\"libelle\"). En-tetes lus : " + String.join(", ", table.headers()));
        }

        List<Product> catalogue = products.findAllWithCategory();
        Catalogue index = Catalogue.of(catalogue);
        List<Category> rayons = categories.findAllWithParent();

        List<Draft> drafts = new ArrayList<>();
        // Both keys point at the same draft, so a second line carrying only the barcode and a
        // third carrying only the name still fold into the first.
        Map<String, Draft> seen = new HashMap<>();

        for (CsvTable.Row row : table.rows()) {
            Draft draft = read(row, mapping);
            Draft twin = draft.name.isBlank() ? null : findTwin(draft, seen);
            if (twin != null) {
                twin.absorb(draft);
                drafts.add(draft);
                continue;
            }
            drafts.add(draft);
            if (!draft.name.isBlank()) {
                seen.put(CsvTable.normalise(draft.name), draft);
                if (draft.barcode != null) {
                    seen.putIfAbsent("barcode:" + draft.barcode, draft);
                }
            }
        }

        // Names this file will bring into the catalogue, so a suggested rename does not collide
        // with a product that does not exist yet either.
        Set<String> reserved = new LinkedHashSet<>(index.byName.keySet());
        for (Draft draft : drafts) {
            if (draft.folded == null) {
                match(draft, index, rayons, reserved);
            }
        }

        return summarise(table, mapping, drafts, rayons);
    }

    // ----------------------------------------------------------------------- apply

    /**
     * Writes the ticked lines: raises the stock of the products merged into, adds the others,
     * and creates the rayons the file named.
     *
     * <p>Every line is matched again here rather than trusted from the preview. The preview may
     * be minutes old, and in between a colleague can have created the very product this line is
     * about, or taken its barcode. A line that can no longer be applied as asked is reported
     * refused and the rest still go in -- failing three hundred lines over one is not a service
     * to anybody.
     */
    @RequiresActiveLicense
    @Transactional
    public ProductImportResultResponse apply(ProductImportRequest request) {
        Catalogue index = Catalogue.of(products.findAllWithCategory());
        RayonResolver rayons = new RayonResolver();
        List<ProductImportResultResponse.Line> lines = new ArrayList<>();
        int created = 0;
        int merged = 0;
        int skipped = 0;
        int unitsAdded = 0;

        for (ProductImportLineRequest line : request.lines()) {
            String name = line.name().trim();

            if (line.action() == ProductImportAction.MERGE) {
                Optional<Product> target = line.mergeIntoId() == null
                        ? Optional.empty()
                        : products.findByIdForUpdate(line.mergeIntoId());
                if (target.isEmpty()) {
                    skipped++;
                    lines.add(refused(line, name, "Le produit a completer n'existe plus : "
                            + "relancez l'import pour cette ligne."));
                    continue;
                }
                Product product = target.get();
                product.adjustStock(line.quantity());
                // The file is read as a delivery note, so the shop's own name, price and rayon
                // stand. A unit it never had is the exception: filling a blank is not a change
                // of description, and it is the field a first import most often supplies.
                if (isBlank(product.getUnit()) && !isBlank(line.unit())) {
                    product.setUnit(trimmedUnit(line.unit()));
                }
                merged++;
                unitsAdded += line.quantity();
                lines.add(new ProductImportResultResponse.Line(line.line(), product.getName(),
                        ProductImportOutcome.MERGED, product.getId(), ""));
                continue;
            }

            // CREATE. The rayon is resolved here and not above, because a merge does not use one:
            // resolving it for every line would have the category column of a merge line grow a
            // rayon nobody asked for and nothing is filed under.
            Category rayon = rayons.resolve(line);

            // The name is freed rather than refused: two identically named references would make
            // every later import ambiguous, and the operator asked for a second product, not for
            // this one to be dropped.
            String free = freeName(name, index.byName.keySet());
            List<String> notes = new ArrayList<>();
            if (!free.equals(name)) {
                notes.add("\"" + name + "\" etait deja pris, la reference a ete creee sous \""
                        + free + "\".");
            }

            String barcode = trimmedOrNull(line.barcode());
            if (barcode != null && index.byBarcode.containsKey(barcode)) {
                notes.add("Le code-barres " + barcode + " appartient deja a \""
                        + index.byBarcode.get(barcode).getName()
                        + "\" : la reference est creee sans code-barres.");
                barcode = null;
            }

            Product product = products.save(Product.builder()
                    .name(free)
                    .price(line.price())
                    .stockQuantity(line.quantity())
                    .unit(trimmedUnit(line.unit()))
                    .barcode(barcode)
                    .category(rayon)
                    .build());
            index.add(product);

            created++;
            unitsAdded += line.quantity();
            lines.add(new ProductImportResultResponse.Line(line.line(), product.getName(),
                    free.equals(name) ? ProductImportOutcome.CREATED : ProductImportOutcome.RENAMED,
                    product.getId(), String.join(" ", notes)));
        }

        return new ProductImportResultResponse(created, merged, skipped, unitsAdded,
                rayons.created(), List.copyOf(lines));
    }

    /**
     * Where each line's rayon comes from, resolved once per distinct path.
     *
     * <p>The memo is the point. Three hundred lines of a grocery's file name maybe eight
     * rayons, and both {@code findByPath} and {@code ensurePath} read the whole category table
     * to answer -- so resolving per line would be three hundred reads of the same eight rows.
     * It also makes "which rayons did this import create" exact: a path is new the first time
     * it is asked for, and not the second.
     */
    private final class RayonResolver {
        private final Map<String, Category> resolved = new HashMap<>();
        private final Set<String> created = new LinkedHashSet<>();

        Category resolve(ProductImportLineRequest line) {
            if (line.categoryId() != null) {
                return categories.findById(line.categoryId()).orElse(null);
            }
            String path = line.categoryPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String key = CsvTable.normalise(String.join("/", CategoryService.segmentsOf(path)));
            if (resolved.containsKey(key)) {
                return resolved.get(key);
            }
            boolean existed = categoryService.findByPath(path).isPresent();
            Category rayon = categoryService.ensurePath(path).orElse(null);
            resolved.put(key, rayon);
            if (rayon != null && !existed) {
                created.add(CategoryService.pathOf(rayon));
            }
            return rayon;
        }

        List<String> created() {
            return List.copyOf(created);
        }
    }

    private static ProductImportResultResponse.Line refused(ProductImportLineRequest line,
                                                            String name, String why) {
        return new ProductImportResultResponse.Line(line.line(), name,
                ProductImportOutcome.SKIPPED, null, why);
    }

    // ------------------------------------------------------------- reading one row

    /** One line of the file, as read, before it meets the catalogue. */
    private static final class Draft {
        int line;
        String name = "";
        int quantity;
        String unit;
        BigDecimal price;
        String barcode;
        String categoryPath = "";
        Long categoryId;
        ProductImportOutcome outcome = ProductImportOutcome.CREATED;
        ProductImportAction action = ProductImportAction.CREATE;
        Product existing;
        String matchedOn;
        String suggestedName;
        boolean selected = true;
        /** Set once folded into an earlier line of the same file; that line owns the quantity. */
        Draft folded;
        final List<String> notes = new ArrayList<>();

        /** Takes over a duplicate line's quantity, and says on both rows what happened. */
        void absorb(Draft duplicate) {
            quantity += duplicate.quantity;
            notes.add("La ligne " + duplicate.line + " porte le meme produit : les quantites "
                    + "ont ete additionnees ici.");
            duplicate.folded = this;
            duplicate.selected = false;
            duplicate.outcome = ProductImportOutcome.SKIPPED;
            duplicate.notes.add("Doublon dans le fichier : la quantite a ete ajoutee a la ligne "
                    + line + ".");
        }
    }

    private static Draft read(CsvTable.Row row, Mapping mapping) {
        Draft draft = new Draft();
        draft.line = row.line();
        draft.name = mapping.cell(row, Column.NAME).replaceAll("\\s+", " ").trim();
        draft.unit = trimmedOrNull(mapping.cell(row, Column.UNIT));
        draft.barcode = trimmedOrNull(mapping.cell(row, Column.BARCODE));
        draft.categoryPath = mapping.cell(row, Column.CATEGORY).trim();

        if (draft.name.isEmpty()) {
            draft.outcome = ProductImportOutcome.SKIPPED;
            draft.selected = false;
            draft.notes.add("Ligne sans nom de produit : rien a importer.");
            return draft;
        }

        String rawQuantity = mapping.cell(row, Column.QUANTITY);
        BigDecimal quantity = ProductImportColumns.number(rawQuantity);
        if (!rawQuantity.isBlank() && quantity == null) {
            draft.notes.add("Quantite illisible (\"" + rawQuantity + "\") : lue comme 0.");
        } else if (quantity != null && quantity.signum() < 0) {
            draft.notes.add("Quantite negative (\"" + rawQuantity + "\") : lue comme 0. "
                    + "Un import ajoute du stock, il n'en retire pas.");
            quantity = null;
        } else if (quantity != null && quantity.stripTrailingZeros().scale() > 0) {
            // The stock is held in whole units. A shop weighing its goods needs a decimal
            // stock everywhere, not a rounding hidden in the importer -- so this is said.
            draft.notes.add("Quantite \"" + rawQuantity + "\" arrondie a "
                    + quantity.setScale(0, RoundingMode.HALF_UP) + " : le stock se compte en "
                    + "unites entieres.");
        }
        draft.quantity = quantity == null ? 0 : quantity.setScale(0, RoundingMode.HALF_UP).intValue();

        String rawPrice = mapping.cell(row, Column.PRICE);
        BigDecimal price = ProductImportColumns.number(rawPrice);
        if (price != null && price.signum() < 0) {
            draft.notes.add("Prix negatif (\"" + rawPrice + "\") : a saisir avant d'importer.");
            price = null;
        } else if (!rawPrice.isBlank() && price == null) {
            draft.notes.add("Prix illisible (\"" + rawPrice + "\") : a saisir avant d'importer.");
        }
        draft.price = price == null ? null : price.setScale(2, RoundingMode.HALF_UP);

        if (draft.unit != null && draft.unit.length() > Product.MAX_UNIT_LENGTH) {
            draft.notes.add("Unite \"" + draft.unit + "\" trop longue : raccourcie a "
                    + Product.MAX_UNIT_LENGTH + " caracteres.");
            draft.unit = draft.unit.substring(0, Product.MAX_UNIT_LENGTH).trim();
        }
        return draft;
    }

    /** The earlier line of this file carrying the same product, or null. */
    private static Draft findTwin(Draft draft, Map<String, Draft> seen) {
        if (draft.barcode != null) {
            Draft byBarcode = seen.get("barcode:" + draft.barcode);
            if (byBarcode != null) {
                return byBarcode;
            }
        }
        return seen.get(CsvTable.normalise(draft.name));
    }

    // ------------------------------------------------------ matching the catalogue

    /**
     * Decides what this line means against the catalogue, and writes the row's default state.
     *
     * <p>A barcode match is an identity, a name match is a guess, and the two are not treated
     * alike. When the file gives a barcode the shop does not know but the name is taken, the
     * two are most likely different products under one wording -- "Lait 1L" from two dairies --
     * so the row defaults to creating a second reference under a freed name, and says why. That
     * is the one case the operator cannot be spared: only they know.
     */
    private void match(Draft draft, Catalogue index, List<Category> rayons, Set<String> reserved) {
        draft.categoryId = categoryService.findByPath(draft.categoryPath, rayons)
                .map(Category::getId)
                .orElse(null);

        if (draft.outcome == ProductImportOutcome.SKIPPED) {
            return;
        }
        if (draft.price == null) {
            draft.notes.add("Aucun prix dans le fichier : saisissez-le sur la ligne.");
        }

        Product byBarcode = draft.barcode == null ? null : index.byBarcode.get(draft.barcode);
        if (byBarcode != null) {
            draft.existing = byBarcode;
            draft.matchedOn = "barcode";
            draft.action = ProductImportAction.MERGE;
            draft.outcome = ProductImportOutcome.MERGED;
            return;
        }

        Product byName = index.byName.get(CsvTable.normalise(draft.name));
        if (byName == null) {
            reserved.add(CsvTable.normalise(draft.name));
            return;
        }

        draft.existing = byName;
        draft.matchedOn = "name";
        boolean conflictingBarcode = draft.barcode != null
                && !isBlank(byName.getBarcode())
                && !draft.barcode.equals(byName.getBarcode());
        if (conflictingBarcode) {
            draft.outcome = ProductImportOutcome.RENAMED;
            draft.action = ProductImportAction.CREATE;
            draft.suggestedName = freeName(draft.name, reserved);
            reserved.add(CsvTable.normalise(draft.suggestedName));
            draft.notes.add("Meme nom que \"" + byName.getName() + "\" mais un autre code-barres"
                    + " : traite comme un produit different. Utilisez \"Fusionner\" si c'est le "
                    + "meme article.");
            return;
        }
        draft.action = ProductImportAction.MERGE;
        draft.outcome = ProductImportOutcome.MERGED;
        draft.suggestedName = freeName(draft.name, reserved);
    }

    /** A name nobody holds yet: the name itself, else the name with " (2)", " (3)"... */
    private static String freeName(String name, Set<String> takenFolded) {
        if (!takenFolded.contains(CsvTable.normalise(name))) {
            return name;
        }
        for (int suffix = 2; suffix < MAX_RENAME_ATTEMPTS; suffix++) {
            String candidate = name + " (" + suffix + ")";
            if (!takenFolded.contains(CsvTable.normalise(candidate))) {
                return candidate;
            }
        }
        throw new BusinessRuleException("IMPORT_NAME_EXHAUSTED",
                "Trop de produits portent deja le nom \"" + name + "\".");
    }

    // ------------------------------------------------------------------- the reply

    private ProductImportPreviewResponse summarise(CsvTable table, Mapping mapping,
                                                   List<Draft> drafts, List<Category> rayons) {
        Map<Long, String> rayonPaths = new HashMap<>();
        rayons.forEach(rayon -> rayonPaths.put(rayon.getId(), CategoryService.pathOf(rayon)));

        List<ProductImportLineResponse> lines = drafts.stream()
                .map(draft -> new ProductImportLineResponse(
                        draft.line,
                        draft.name,
                        draft.quantity,
                        draft.unit,
                        draft.price,
                        draft.barcode,
                        draft.categoryPath,
                        draft.categoryId,
                        draft.outcome,
                        draft.action,
                        draft.existing == null ? null : ProductRefResponse.of(draft.existing,
                                draft.existing.getCategory() == null ? null
                                        : rayonPaths.get(draft.existing.getCategory().getId())),
                        draft.matchedOn,
                        draft.suggestedName,
                        draft.selected,
                        List.copyOf(draft.notes)))
                .toList();

        // Rayons the file names that the shop has not got: created when the import is applied,
        // and worth showing beforehand because a typo in that column grows a rayon for good.
        //
        // Merge lines are left out, and must be: their product keeps the rayon the shop already
        // filed it under, so their category cell is never read and announcing it here would
        // promise a rayon the apply will not create.
        List<String> createdRayons = drafts.stream()
                .filter(draft -> draft.selected
                        && draft.action == ProductImportAction.CREATE
                        && draft.categoryId == null)
                .map(draft -> draft.categoryPath)
                .filter(path -> !path.isBlank())
                .map(path -> String.join(CategoryService.PATH_SEPARATOR,
                        CategoryService.segmentsOf(path)))
                .distinct()
                .toList();

        return new ProductImportPreviewResponse(
                String.valueOf(table.separator()),
                lines.size(),
                (int) count(drafts, ProductImportOutcome.CREATED),
                (int) count(drafts, ProductImportOutcome.MERGED),
                (int) count(drafts, ProductImportOutcome.RENAMED),
                (int) count(drafts, ProductImportOutcome.SKIPPED),
                (int) drafts.stream()
                        .filter(draft -> draft.selected
                                && draft.action == ProductImportAction.CREATE
                                && draft.categoryId == null
                                && draft.categoryPath.isBlank())
                        .count(),
                mapping.recognised().entrySet().stream()
                        .map(entry -> entry.getKey() + " : " + entry.getValue())
                        .toList(),
                mapping.ignored(),
                mapping.missing(),
                createdRayons,
                lines);
    }

    private static long count(List<Draft> drafts, ProductImportOutcome outcome) {
        return drafts.stream().filter(draft -> draft.outcome == outcome).count();
    }

    // ------------------------------------------------------------------- catalogue

    /**
     * The catalogue in two lookup tables, kept up to date as the import adds to it.
     *
     * <p>Held in memory for the length of one call rather than queried per line: an import of
     * five hundred lines would otherwise be a thousand round trips, and the whole catalogue of a
     * grocery is what the products screen already loads on every visit.
     */
    private static final class Catalogue {
        private final Map<String, Product> byName = new LinkedHashMap<>();
        private final Map<String, Product> byBarcode = new HashMap<>();

        static Catalogue of(List<Product> all) {
            Catalogue catalogue = new Catalogue();
            all.forEach(catalogue::add);
            return catalogue;
        }

        void add(Product product) {
            // First wins: two products named alike can only come from a database written
            // around this service, and the older reference is the one earlier imports matched.
            byName.putIfAbsent(CsvTable.normalise(product.getName()), product);
            if (!isBlank(product.getBarcode())) {
                byBarcode.putIfAbsent(product.getBarcode().trim(), product);
            }
        }
    }

    // ----------------------------------------------------------------- small stuff

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimmedOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimmedUnit(String value) {
        String trimmed = trimmedOrNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() <= Product.MAX_UNIT_LENGTH
                ? trimmed
                : trimmed.substring(0, Product.MAX_UNIT_LENGTH).trim();
    }
}
