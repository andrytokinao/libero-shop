package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.Category;
import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.entity.ProductPackaging;
import com.houssen.liberoshop.entity.Supplier;
import com.houssen.liberoshop.entity.Supply;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.license.RequiresActiveLicense;
import com.houssen.liberoshop.repository.CategoryRepository;
import com.houssen.liberoshop.repository.ProductPackagingRepository;
import com.houssen.liberoshop.repository.ProductRepository;
import com.houssen.liberoshop.repository.StockMovementRepository;
import com.houssen.liberoshop.repository.SupplierRepository;
import com.houssen.liberoshop.service.ProductImportColumns.Column;
import com.houssen.liberoshop.service.ProductImportColumns.Mapping;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.util.Quantities;
import com.houssen.liberoshop.web.dto.PackagingRequest;
import com.houssen.liberoshop.web.dto.ProductImportLineRequest;
import com.houssen.liberoshop.web.dto.ProductImportLineResponse;
import com.houssen.liberoshop.web.dto.ProductImportPreviewResponse;
import com.houssen.liberoshop.web.dto.ProductImportRequest;
import com.houssen.liberoshop.web.dto.ProductImportResultResponse;
import com.houssen.liberoshop.web.dto.ProductRefResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
 * <p><b>Every unit is accounted for.</b> Each line that raises a quantity writes a {@code Supply}
 * naming the account that ran the import, so a stock that went up can always be traced to
 * somebody and a moment -- the same guarantee a delivery booked at the depot gives. The supplier
 * is optional and is the one thing that differs: a file from a wholesaler names them, an
 * inventory count leaves them null. Nothing is ever written in an operator's name from the
 * payload; the actor is read from the session by the controller and passed in here.
 *
 * <p><b>Units.</b> A line may be in any unit of its product -- "2 tonne", then "200 kapoka" -- and
 * is converted to the base unit while reading, so whoever fills the file computes nothing. The
 * units come from the workbook's "Unités" sheet ({@link ProductUnitSheet}) and from the ones the
 * product already has. The sheet's units are written with the product, judged by the same
 * {@link PackagingRules} as the units dialog; a refused unit never refuses its line -- the product
 * and its stock still go in, and the line says which unit was left out.
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
    private final SupplierRepository suppliers;
    private final StockMovementRepository movements;
    private final ProductPackagingRepository packagings;
    private final PackagingRules packagingRules;
    private final CategoryService categoryService;
    private final BusinessCalendar calendar;
    private final ApplicationEventPublisher events;

    public ProductImportService(ProductRepository products, CategoryRepository categories,
                                SupplierRepository suppliers, StockMovementRepository movements,
                                ProductPackagingRepository packagings, PackagingRules packagingRules,
                                CategoryService categoryService, BusinessCalendar calendar,
                                ApplicationEventPublisher events) {
        this.products = products;
        this.categories = categories;
        this.suppliers = suppliers;
        this.movements = movements;
        this.packagings = packagings;
        this.packagingRules = packagingRules;
        this.categoryService = categoryService;
        this.calendar = calendar;
        this.events = events;
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
        return preview(CsvTable.parse(content), ProductUnitSheet.empty());
    }

    /** The same preview over a table already read, with no "Unités" sheet. */
    public ProductImportPreviewResponse preview(CsvTable table) {
        return preview(table, ProductUnitSheet.empty());
    }

    /**
     * The preview of an Excel workbook -- see {@link SpreadsheetReader} -- whose "Unités" sheet
     * declares the units the products sheet's lines may be written in.
     */
    public ProductImportPreviewResponse preview(SpreadsheetReader.Sheets workbook) {
        return preview(workbook.first(), workbook.named(ProductUnitSheet.SHEET_NAMES)
                .map(ProductUnitSheet::read)
                .orElse(ProductUnitSheet.empty()));
    }

    /**
     * @param units the units the lines may be written in, beside the ones the products already
     *              have; {@link ProductUnitSheet#empty()} for a file without them
     */
    public ProductImportPreviewResponse preview(CsvTable table, ProductUnitSheet units) {
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
            if (draft.outcome != ImportOutcome.SKIPPED) {
                // Before folding: two lines of one product add up in base units, not in theirs.
                toBaseUnit(draft, index, units);
            }
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

        return summarise(table, mapping, drafts, rayons, units);
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
    public ProductImportResultResponse apply(ProductImportRequest request, UserApp operator) {
        Catalogue index = Catalogue.of(products.findAllWithCategory());
        RayonResolver rayons = new RayonResolver();
        Supplier supplier = request.supplierId() == null ? null
                : suppliers.findById(request.supplierId())
                        .orElseThrow(() -> ResourceNotFoundException.of("Fournisseur",
                                request.supplierId()));
        // One timestamp for the whole file, not one per line: the entries are a single act, and
        // spreading them over the seconds the loop takes would make the supplies list look like
        // a trickle of deliveries.
        LocalDateTime enteredAt = calendar.now();

        List<ProductImportResultResponse.Line> lines = new ArrayList<>();
        int created = 0;
        int merged = 0;
        int skipped = 0;
        BigDecimal unitsAdded = Quantities.of(0);
        int written = 0;
        int unitsCreated = 0;
        // Every product written, so the screens showing them get the new figures.
        List<Long> touched = new ArrayList<>();

        for (ProductImportLineRequest line : request.lines()) {
            String name = line.name().trim();

            if (line.action() == ImportAction.MERGE) {
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
                BigDecimal unitCost = PurchaseCosting.scaled(line.cost());
                PurchaseCosting.receive(product, line.quantity(), unitCost);
                // The file is read as a delivery note, so the shop's own name, price and rayon
                // stand. A unit it never had is the exception: filling a blank is not a change
                // of description, and it is the field a first import most often supplies.
                if (isBlank(product.getUnit()) && !isBlank(line.unit())) {
                    product.setUnit(trimmedUnit(line.unit()));
                }
                merged++;
                touched.add(product.getId());
                unitsAdded = unitsAdded.add(line.quantity());
                written += book(product, line.quantity(), unitCost, supplier, operator, enteredAt);
                List<String> notes = new ArrayList<>();
                unitsCreated += addUnits(product, line.packagings(), true, notes);
                lines.add(new ProductImportResultResponse.Line(line.line(), product.getName(),
                        ImportOutcome.MERGED, product.getId(), String.join(" ", notes)));
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
            } else if (barcode != null && packagings.existsByBarcode(barcode)) {
                // A sack's own code: one code, one thing to sell, or the scanner has to guess.
                notes.add("Le code-barres " + barcode + " est celui d'une unite d'un autre produit"
                        + " : la reference est creee sans code-barres.");
                barcode = null;
            }

            Product fresh = Product.builder()
                    .name(free)
                    .price(line.price())
                    .stockQuantity(Quantities.of(0))
                    .unit(trimmedUnit(line.unit()))
                    .barcode(barcode)
                    .category(rayon)
                    .build();
            // Received like any delivery, so a new reference starts with its cost as average.
            BigDecimal unitCost = PurchaseCosting.scaled(line.cost());
            PurchaseCosting.receive(fresh, line.quantity(), unitCost);
            Product product = products.save(fresh);
            index.add(product);

            created++;
            touched.add(product.getId());
            unitsAdded = unitsAdded.add(line.quantity());
            written += book(product, line.quantity(), unitCost, supplier, operator, enteredAt);
            unitsCreated += addUnits(product, line.packagings(), false, notes);
            lines.add(new ProductImportResultResponse.Line(line.line(), product.getName(),
                    free.equals(name) ? ImportOutcome.CREATED : ImportOutcome.RENAMED,
                    product.getId(), String.join(" ", notes)));
        }

        if (!touched.isEmpty()) {
            events.publishEvent(new StockChangedEvent(touched));
        }
        return new ProductImportResultResponse(created, merged, skipped, unitsAdded, written,
                supplier == null ? null : supplier.getName(),
                rayons.created(), unitsCreated, List.copyOf(lines));
    }

    /**
     * Gives a product the extra units its line carries, and returns how many were written.
     *
     * <p>Each unit is judged on its own by {@link PackagingRules}; a refused one is noted on the
     * line and the others still go in. This is why the rules are not called through a
     * transactional service: a refusal crossing a {@code @Transactional} proxy would mark the
     * whole import for rollback.
     *
     * @param merging true when the product existed: the units the shop already set stand, like
     *                its name and price, and only the missing ones are added
     * @param notes   the line's notes, which a refusal is appended to
     */
    private int addUnits(Product product, List<PackagingRequest> requested, boolean merging,
                         List<String> notes) {
        int written = 0;
        for (PackagingRequest request : requested) {
            if (merging && packagingRules.hasUnit(product, request.label())) {
                continue;
            }
            ProductPackaging packaging = ProductPackaging.builder().product(product).build();
            try {
                packagingRules.applyTo(request, packaging);
                packagings.save(packaging);
                written++;
            } catch (BusinessRuleException refused) {
                notes.add("Unite \"" + request.label() + "\" non creee : " + refused.getMessage());
            }
        }
        return written;
    }

    /**
     * Records why a product's stock went up, and returns 1 if it wrote anything.
     *
     * <p>A line at quantity zero writes nothing. Those are common -- a catalogue file listing
     * references the shop does not hold yet -- and a movement of zero units would be noise in
     * the one list a manager reads to find out what came in.
     *
     * @param unitCost what one unit cost, or null when the file did not say
     * @param supplier null for an inventory count; see {@link com.houssen.liberoshop.entity.Supply}
     */
    private int book(Product product, BigDecimal quantity, BigDecimal unitCost, Supplier supplier,
                     UserApp operator, LocalDateTime enteredAt) {
        if (quantity.signum() <= 0) {
            return 0;
        }
        movements.save(Supply.builder()
                .quantity(quantity)
                .movementDate(enteredAt)
                .product(product)
                .performedBy(operator)
                .supplier(supplier)
                .unitCost(unitCost)
                .build());
        return 1;
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
                ImportOutcome.SKIPPED, null, why);
    }

    // ------------------------------------------------------------- reading one row

    /** One line of the file, as read, before it meets the catalogue. */
    private static final class Draft {
        int line;
        String name = "";
        BigDecimal quantity = Quantities.of(0);
        String unit;
        BigDecimal price;
        /** Purchase price of one unit, or null: optional, so never a reason to refuse the line. */
        BigDecimal cost;
        String barcode;
        String categoryPath = "";
        Long categoryId;
        ImportOutcome outcome = ImportOutcome.CREATED;
        ImportAction action = ImportAction.CREATE;
        Product existing;
        String matchedOn;
        String suggestedName;
        boolean selected = true;
        /** Set once folded into an earlier line of the same file; that line owns the quantity. */
        Draft folded;
        final List<String> notes = new ArrayList<>();
        /** The extra units read from the line that passed the checks a file can be put to. */
        final List<PackagingRequest> packagings = new ArrayList<>();

        /**
         * Takes over a duplicate line's quantity, and says on both rows what happened. Two costs
         * are blended the way two deliveries would be, since that is what the lines describe.
         */
        void absorb(Draft duplicate) {
            cost = PurchaseCosting.scaled(PurchaseCosting.averageAfterReceipt(quantity, cost, duplicate.quantity,
                    duplicate.cost));
            quantity = quantity.add(duplicate.quantity);
            if (packagings.isEmpty()) {
                // The units described once, on whichever line, are the product's.
                packagings.addAll(duplicate.packagings);
            }
            notes.add("La ligne " + duplicate.line + " porte le meme produit : les quantites "
                    + "ont ete additionnees ici.");
            duplicate.folded = this;
            duplicate.selected = false;
            duplicate.outcome = ImportOutcome.SKIPPED;
            // Said as what happened, not as a mistake: one product received in two units -- "2
            // tonne", then "200 kapoka" -- is two lines on purpose.
            duplicate.notes.add("Meme produit que la ligne " + line + " : sa quantite y est ajoutee.");
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
            draft.outcome = ImportOutcome.SKIPPED;
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
        } else if (quantity != null && Quantities.tooPrecise(quantity)) {
            // The stock is held to the thousandth. Finer than that is a spreadsheet artefact
            // rather than a weighing, but rounding is still said rather than hidden.
            draft.notes.add("Quantite \"" + rawQuantity + "\" arrondie a "
                    + Quantities.format(Quantities.scaled(quantity)) + " : le stock se compte "
                    + "au millieme.");
        }
        draft.quantity = quantity == null ? Quantities.of(0) : Quantities.scaled(quantity);

        String rawPrice = mapping.cell(row, Column.PRICE);
        BigDecimal price = ProductImportColumns.number(rawPrice);
        if (price != null && price.signum() < 0) {
            draft.notes.add("Prix negatif (\"" + rawPrice + "\") : a saisir avant d'importer.");
            price = null;
        } else if (!rawPrice.isBlank() && price == null) {
            draft.notes.add("Prix illisible (\"" + rawPrice + "\") : a saisir avant d'importer.");
        }
        draft.price = price == null ? null : price.setScale(2, RoundingMode.HALF_UP);

        String rawCost = mapping.cell(row, Column.COST);
        BigDecimal cost = ProductImportColumns.number(rawCost);
        if (cost != null && cost.signum() < 0) {
            draft.notes.add("Prix d'achat negatif (\"" + rawCost + "\") : ignore.");
            cost = null;
        } else if (!rawCost.isBlank() && cost == null) {
            draft.notes.add("Prix d'achat illisible (\"" + rawCost + "\") : ignore.");
        }
        draft.cost = PurchaseCosting.scaled(cost);

        if (draft.unit != null && draft.unit.length() > Product.MAX_UNIT_LENGTH) {
            draft.notes.add("Unite \"" + draft.unit + "\" trop longue : raccourcie a "
                    + Product.MAX_UNIT_LENGTH + " caracteres.");
            draft.unit = draft.unit.substring(0, Product.MAX_UNIT_LENGTH).trim();
        }
        return draft;
    }

    /**
     * Puts a line in its product's base unit: "2 tonne" of rice counted in kapoka becomes 7 000
     * kapoka, its purchase price one 3 500th of a tonne's. The line says what was converted.
     *
     * <p>The units are the ones the "Unités" sheet declares for the product, and the ones it
     * already has -- which win over the sheet, like the rest of what the shop set. A line with no
     * unit is in the base unit. A line in a unit nobody declared is refused rather than guessed:
     * read as base units, "2 tonne" would enter as 2 kapoka.
     *
     * <p>A product with no units anywhere is left as the file wrote it, which is how every line
     * was read before units: its unit column names the base unit.
     */
    private static void toBaseUnit(Draft draft, Catalogue index, ProductUnitSheet sheet) {
        Product known = index.find(draft.barcode, draft.name);
        ProductUnitSheet.Definition declared = sheet.of(draft.name).orElse(null);
        String base = known != null && known.getUnit() != null ? known.getUnit()
                : declared == null ? null : declared.baseUnit();

        if (declared != null && known != null && known.getUnit() != null && declared.baseUnit() != null
                && !CsvTable.normalise(declared.baseUnit()).equals(CsvTable.normalise(known.getUnit()))) {
            draft.notes.add("La feuille " + ProductUnitSheet.SHEET_NAME + " compte ce produit en "
                    + declared.baseUnit() + ", le catalogue en " + known.getUnit()
                    + " : ses unites de la feuille sont ignorees.");
            declared = null;
        }
        boolean knownUnits = known != null && !known.getPackagings().isEmpty();
        if (declared == null && !knownUnits) {
            return;
        }

        // Base unit first, then the product's own units, then the sheet's: the first one of a
        // name wins, so the shop's own factor stands against the file's.
        Map<String, Unit> units = new LinkedHashMap<>();
        if (base != null) {
            units.put(CsvTable.normalise(base), new Unit(base, BigDecimal.ONE));
        }
        if (known != null) {
            known.getPackagings().forEach(p ->
                    units.putIfAbsent(CsvTable.normalise(p.getLabel()), new Unit(p.getLabel(), p.getFactor())));
        }
        List<PackagingRequest> resolved = new ArrayList<>();
        if (declared != null) {
            // The sheet's units, references first: each one's factor is its contenance times its
            // reference's -- a sack of 25 kg is 25 × 3.5 kapoka -- with the shop's own units
            // standing wherever the sheet names one of them.
            for (PackagingRequest request : declared.packagings()) {
                Unit reference = request.reference() == null ? units.get(CsvTable.normalise(base))
                        : units.get(CsvTable.normalise(request.reference()));
                if (request.reference() != null && reference == null) {
                    draft.notes.add("Unite \"" + request.label() + "\" ignoree : son unite de reference \""
                            + request.reference() + "\" n'existe pas pour ce produit.");
                    continue;
                }
                BigDecimal multiplier = reference == null ? BigDecimal.ONE : reference.factor();
                try {
                    BigDecimal factor = PackagingRules.validFactor(request.factor().multiply(multiplier), base);
                    units.putIfAbsent(CsvTable.normalise(request.label()), new Unit(request.label(), factor));
                    resolved.add(request);
                } catch (BusinessRuleException refused) {
                    draft.notes.add("Unite \"" + request.label() + "\" ignoree : " + refused.getMessage());
                }
            }
        }

        Unit unit = draft.unit == null ? units.get(CsvTable.normalise(base)) : units.get(CsvTable.normalise(draft.unit));
        if (unit == null && base == null) {
            // The sheet declared the other units but not the base: the line's unit is that base.
            base = draft.unit;
            unit = base == null ? null : new Unit(base, BigDecimal.ONE);
        }
        if (unit == null && draft.unit == null) {
            refuse(draft, "Ligne sans unite, et la feuille " + ProductUnitSheet.SHEET_NAME
                    + " ne dit pas laquelle est l'unite de base (contenance 1).");
            return;
        }
        if (unit == null) {
            refuse(draft, "Unite \"" + draft.unit + "\" inconnue pour ce produit (unites : "
                    + String.join(", ", units.values().stream().map(Unit::label).toList())
                    + ") : ajoutez-la a la feuille " + ProductUnitSheet.SHEET_NAME + ".");
            return;
        }
        if (unit.factor().compareTo(BigDecimal.ONE) != 0) {
            BigDecimal entered = draft.quantity;
            draft.quantity = Quantities.scaled(entered.multiply(unit.factor()));
            draft.notes.add(Quantities.format(entered) + " " + unit.label() + " = "
                    + Quantities.format(draft.quantity) + " " + base + ".");
            if (draft.cost != null) {
                draft.cost = draft.cost.divide(unit.factor(), PurchaseCosting.COST_SCALE, RoundingMode.HALF_UP);
            }
            if (draft.price != null) {
                draft.notes.add("Le prix est lu comme celui d'un(e) " + base
                        + " : celui des autres unites vient de la feuille " + ProductUnitSheet.SHEET_NAME + ".");
            }
        }
        draft.unit = base;
        if (declared != null) {
            if (draft.price == null && declared.basePrice() != null) {
                draft.price = declared.basePrice().setScale(2, RoundingMode.HALF_UP);
            }
            if (draft.barcode == null) {
                draft.barcode = declared.baseBarcode();
            }
            draft.packagings.clear();
            draft.packagings.addAll(resolved);
        }
    }

    /** A unit a line may be written in: its name, and how many base units it holds. */
    private record Unit(String label, BigDecimal factor) {
    }

    /** A line the reader cannot apply as written: unticked, unusable, and saying why. */
    private static void refuse(Draft draft, String why) {
        draft.outcome = ImportOutcome.SKIPPED;
        draft.selected = false;
        draft.notes.add(why);
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

        if (draft.outcome == ImportOutcome.SKIPPED) {
            return;
        }
        if (draft.price == null) {
            draft.notes.add("Aucun prix dans le fichier : saisissez-le sur la ligne.");
        }

        Product byBarcode = draft.barcode == null ? null : index.byBarcode.get(draft.barcode);
        if (byBarcode != null) {
            draft.existing = byBarcode;
            draft.matchedOn = "barcode";
            draft.action = ImportAction.MERGE;
            draft.outcome = ImportOutcome.MERGED;
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
            draft.outcome = ImportOutcome.RENAMED;
            draft.action = ImportAction.CREATE;
            draft.suggestedName = freeName(draft.name, reserved);
            reserved.add(CsvTable.normalise(draft.suggestedName));
            draft.notes.add("Meme nom que \"" + byName.getName() + "\" mais un autre code-barres"
                    + " : traite comme un produit different. Utilisez \"Fusionner\" si c'est le "
                    + "meme article.");
            return;
        }
        draft.action = ImportAction.MERGE;
        draft.outcome = ImportOutcome.MERGED;
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
                                                   List<Draft> drafts, List<Category> rayons,
                                                   ProductUnitSheet units) {
        Map<Long, String> rayonPaths = new HashMap<>();
        rayons.forEach(rayon -> rayonPaths.put(rayon.getId(), CategoryService.pathOf(rayon)));

        List<ProductImportLineResponse> lines = drafts.stream()
                .map(draft -> new ProductImportLineResponse(
                        draft.line,
                        draft.name,
                        draft.quantity,
                        draft.unit,
                        draft.price,
                        draft.cost,
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
                        List.copyOf(draft.notes),
                        List.copyOf(draft.packagings)))
                .toList();

        // Rayons the file names that the shop has not got: created when the import is applied,
        // and worth showing beforehand because a typo in that column grows a rayon for good.
        //
        // Merge lines are left out, and must be: their product keeps the rayon the shop already
        // filed it under, so their category cell is never read and announcing it here would
        // promise a rayon the apply will not create.
        List<String> createdRayons = drafts.stream()
                .filter(draft -> draft.selected
                        && draft.action == ImportAction.CREATE
                        && draft.categoryId == null)
                .map(draft -> draft.categoryPath)
                .filter(path -> !path.isBlank())
                .map(path -> String.join(CategoryService.PATH_SEPARATOR,
                        CategoryService.segmentsOf(path)))
                .distinct()
                .toList();

        return new ProductImportPreviewResponse(
                table.separator() == CsvTable.NO_SEPARATOR ? "" : String.valueOf(table.separator()),
                lines.size(),
                (int) count(drafts, ImportOutcome.CREATED),
                (int) count(drafts, ImportOutcome.MERGED),
                (int) count(drafts, ImportOutcome.RENAMED),
                (int) count(drafts, ImportOutcome.SKIPPED),
                (int) drafts.stream()
                        .filter(draft -> draft.selected
                                && draft.action == ImportAction.CREATE
                                && draft.categoryId == null
                                && draft.categoryPath.isBlank())
                        .count(),
                mapping.recognised().entrySet().stream()
                        .map(entry -> entry.getKey() + " : " + entry.getValue())
                        .toList(),
                mapping.ignored(),
                mapping.missing(),
                createdRayons,
                units.notes(),
                lines);
    }

    private static long count(List<Draft> drafts, ImportOutcome outcome) {
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

        /** The product a line designates: by its barcode first, else by its name; null for none. */
        Product find(String barcode, String name) {
            Product byCode = barcode == null ? null : byBarcode.get(barcode);
            return byCode != null ? byCode : byName.get(CsvTable.normalise(name));
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
