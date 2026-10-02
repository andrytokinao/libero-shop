package com.houssen.liberoshop.service;

import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.web.dto.PackagingRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The "Unités" sheet of a product import workbook: every unit a product sells in, one row each,
 * linked to the product by its name.
 *
 * <pre>
 * Produit  | Unité     | Contenance | Unité de référence | Prix    | Code barres
 * Riz vrac | kapoka    | 1          |                    | 900     |
 * Riz vrac | kg        | 3,5        | kapoka             | 3000    |
 * Riz vrac | sac 25 kg | 25         | kg                 | 72000   |
 * Riz vrac | tonne     | 1000       | kg                 | 2800000 |
 * </pre>
 *
 * <p>Kept apart from the products sheet on purpose. Units are set once per product; the products
 * sheet is what changes with every delivery, and it stays one line per thing received -- "2
 * tonne", "200 kapoka" -- with no columns to repeat and nothing to compute: the import converts
 * each line with the units declared here.
 *
 * <p>The row of contenance 1 with no reference is the base unit, the one the stock is counted
 * in. Every other row says how many of its reference unit it holds -- the base unit when the
 * reference is left blank -- the way it is printed on the sack: "sac 25 kg" is 25 kg, and the
 * import works out what that is in kapoka. A row may name a reference declared further down;
 * {@link Definition#packagings()} come out in an order where every reference precedes the units
 * written in it. The resulting factors are judged by the same {@link PackagingRules} as the
 * units dialog, once the product's own units are known. A row that fails them is reported and left out;
 * it never stops the rest of the file.
 */
public final class ProductUnitSheet {

    /** More units than this for one product is a sheet gone wrong, not a shop. */
    public static final int MAX_UNITS_PER_PRODUCT = 10;

    /** What the model workbook calls the sheet. */
    public static final String SHEET_NAME = "Unités";

    /** The sheet names accepted, folded: whoever renames the tab still has it read. */
    public static final Set<String> SHEET_NAMES = Set.of(
            "unites", "unite", "units", "conditionnements", "conversions");

    /** The columns of the sheet, each with every spelling accepted for it -- the first is documented. */
    public enum Column {

        PRODUCT("produit", "nom produit", "nom", "article", "designation", "libelle"),

        UNIT("unite", "conditionnement", "unit", "mesure"),

        FACTOR("contenance", "contient", "coefficient", "coef", "facteur", "conversion",
                "equivalence", "nombre", "quantite"),

        /** The unit the contenance is counted in; blank for the base unit. */
        REFERENCE("unite de reference", "reference", "unite reference", "ref", "en"),

        PRICE("prix", "prix de vente", "prix vente", "pv", "price"),

        BARCODE("code barres", "code barre", "barcode", "ean", "gencod");

        private final List<String> aliases;

        Column(String... aliases) {
            this.aliases = List.of(aliases);
        }

        public String label() {
            return aliases.getFirst();
        }

        String[] normalisedAliases() {
            return aliases.stream().map(CsvTable::normalise).toArray(String[]::new);
        }
    }

    /**
     * Every unit one product sells in.
     *
     * @param baseUnit    the row of contenance 1, or null when the sheet gave none -- the
     *                    product's own unit is then the base
     * @param basePrice   that row's price, or null
     * @param baseBarcode that row's barcode, or null
     * @param packagings  the other rows, as the units dialog would send them, every reference
     *                    before the units written in it
     */
    public record Definition(String baseUnit, BigDecimal basePrice, String baseBarcode,
                             List<PackagingRequest> packagings) {
    }

    private static final ProductUnitSheet EMPTY = new ProductUnitSheet(Map.of(), List.of());

    private final Map<String, Definition> byProduct;
    private final List<String> notes;

    private ProductUnitSheet(Map<String, Definition> byProduct, List<String> notes) {
        this.byProduct = byProduct;
        this.notes = notes;
    }

    /** For a file without the sheet -- a CSV, or a workbook of products only. */
    public static ProductUnitSheet empty() {
        return EMPTY;
    }

    /** The units declared for a product, by its name as the products sheet writes it. */
    public Optional<Definition> of(String productName) {
        return Optional.ofNullable(byProduct.get(CsvTable.normalise(productName)));
    }

    /**
     * What the operator should know about the sheet as a whole: what was read, and every row left
     * out with why, naming its line in the sheet.
     */
    public List<String> notes() {
        return notes;
    }

    /** Reads the sheet. Never throws for a bad row: it is noted and the next one is read. */
    public static ProductUnitSheet read(CsvTable table) {
        Map<Column, Integer> columns = new EnumMap<>(Column.class);
        for (Column column : Column.values()) {
            int index = table.indexOf(column.normalisedAliases());
            if (index >= 0) {
                columns.put(column, index);
            }
        }
        List<String> notes = new ArrayList<>();
        for (Column required : List.of(Column.PRODUCT, Column.UNIT, Column.FACTOR)) {
            if (!columns.containsKey(required)) {
                notes.add("Feuille " + SHEET_NAME + " ignoree : il lui manque la colonne \""
                        + required.label() + "\".");
                return new ProductUnitSheet(Map.of(), List.copyOf(notes));
            }
        }

        Map<String, Builder> builders = new LinkedHashMap<>();
        int units = 0;
        for (CsvTable.Row row : table.rows()) {
            String product = PackagingRules.squeeze(cell(row, columns, Column.PRODUCT));
            String unit = PackagingRules.squeeze(cell(row, columns, Column.UNIT));
            if (product == null || unit == null) {
                notes.add(SHEET_NAME + ", ligne " + row.line() + " : produit ou unite manquant, ignoree.");
                continue;
            }
            Builder builder = builders.computeIfAbsent(CsvTable.normalise(product), key -> new Builder());
            try {
                builder.add(unit,
                        ProductImportColumns.number(cell(row, columns, Column.FACTOR)),
                        PackagingRules.squeeze(cell(row, columns, Column.REFERENCE)),
                        ProductImportColumns.number(cell(row, columns, Column.PRICE)),
                        PackagingRules.squeeze(cell(row, columns, Column.BARCODE)));
                units++;
            } catch (BusinessRuleException refused) {
                notes.add(SHEET_NAME + ", ligne " + row.line() + " (" + product + ", " + unit + ") : "
                        + refused.getMessage());
            }
        }
        notes.addFirst("Feuille " + SHEET_NAME + " : " + units + " unite(s) pour "
                + builders.size() + " produit(s).");

        Map<String, Definition> byProduct = new LinkedHashMap<>();
        builders.forEach((product, builder) -> byProduct.put(product, builder.build(notes)));
        return new ProductUnitSheet(Map.copyOf(byProduct), List.copyOf(notes));
    }

    private static String cell(CsvTable.Row row, Map<Column, Integer> columns, Column column) {
        Integer index = columns.get(column);
        return index == null ? "" : row.cell(index);
    }

    /** One product's rows, gathered as they are read. */
    private static final class Builder {
        private String baseUnit;
        private BigDecimal basePrice;
        private String baseBarcode;
        private final Map<String, PackagingRequest> packagings = new LinkedHashMap<>();

        /**
         * A contenance of 1 with no reference is the base unit; anything else must pass
         * {@link PackagingRules#validQuantity} and {@link PackagingRules#validPrice}.
         */
        void add(String unit, BigDecimal factor, String reference, BigDecimal price, String barcode) {
            String folded = CsvTable.normalise(unit);
            if (reference != null && CsvTable.normalise(reference).equals(folded)) {
                throw new BusinessRuleException("PACKAGING_REFERENCE_CYCLE",
                        "une unite ne peut pas etre ecrite en elle-meme.");
            }
            if (reference == null && factor != null && factor.compareTo(BigDecimal.ONE) == 0) {
                if (baseUnit != null && !CsvTable.normalise(baseUnit).equals(folded)) {
                    throw new BusinessRuleException("IMPORT_TWO_BASE_UNITS",
                            "deux unites de contenance 1 (" + baseUnit + " et " + unit
                                    + ") : une seule peut etre l'unite de base.");
                }
                baseUnit = unit;
                basePrice = price == null || price.signum() <= 0 ? null : price;
                baseBarcode = barcode;
                return;
            }
            if (packagings.size() >= MAX_UNITS_PER_PRODUCT) {
                throw new BusinessRuleException("IMPORT_TOO_MANY_UNITS",
                        "au plus " + MAX_UNITS_PER_PRODUCT + " unites par produit.");
            }
            if (packagings.containsKey(folded) || (baseUnit != null && CsvTable.normalise(baseUnit).equals(folded))) {
                throw new BusinessRuleException("IMPORT_UNIT_TWICE", "unite deja declaree pour ce produit.");
            }
            packagings.put(folded, new PackagingRequest(unit,
                    PackagingRules.validQuantity(factor),
                    PackagingRules.validPrice(price),
                    barcode,
                    reference));
        }

        /**
         * The rows, every reference first. A reference to the base unit, or to a unit the sheet
         * does not declare -- the product may already have it -- puts no constraint on the order;
         * rows whose references go round in a circle can never be resolved, and are noted.
         */
        Definition build(List<String> notes) {
            List<PackagingRequest> ordered = new ArrayList<>();
            Set<String> placed = new HashSet<>();
            List<PackagingRequest> waiting = new ArrayList<>(packagings.values());
            boolean progress = true;
            while (!waiting.isEmpty() && progress) {
                progress = false;
                for (var it = waiting.iterator(); it.hasNext(); ) {
                    PackagingRequest request = it.next();
                    String reference = request.reference() == null ? null : CsvTable.normalise(request.reference());
                    if (reference == null || !packagings.containsKey(reference) || placed.contains(reference)) {
                        ordered.add(request);
                        placed.add(CsvTable.normalise(request.label()));
                        it.remove();
                        progress = true;
                    }
                }
            }
            waiting.forEach(request -> notes.add(SHEET_NAME + " : \"" + request.label()
                    + "\" ignoree, ses unites de reference tournent en rond."));
            return new Definition(baseUnit, basePrice, baseBarcode, List.copyOf(ordered));
        }
    }
}
