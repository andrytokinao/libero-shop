package com.houssen.liberoshop.service;

import com.houssen.liberoshop.service.ProductImportColumns.Column;
import com.houssen.liberoshop.service.ProductImportColumns.UnitField;
import com.houssen.liberoshop.util.ExcelUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The model workbook offered next to the import button: the recommended header row, two
 * example lines, and a second sheet saying what each column takes.
 *
 * <p>The headers are the columns of {@link ProductImportColumns} written the way a person
 * would type them; every one of them folds onto its column's first alias, which the tests
 * check by reading the model back through {@link SpreadsheetReader}.
 *
 * <p>After the product columns come those of one extra unit -- "Unité 2", "Contenance 2",
 * "Prix 2", "Code barres 2" -- the most a model needs to show the pattern: a shop selling by the
 * sack as well copies them to "Unité 3".
 *
 * <p>The barcode examples are text, so {@link ExcelUtils} formats that column as text before
 * anyone types in it. Typed into a number column, a thirteen-digit barcode is shown as
 * "6,11E+12" and the operator believes it lost.
 */
public final class ProductImportTemplate {

    public static final String FILE_NAME = "modele-import-produits.xlsx";

    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final Map<Column, String> HEADINGS = Map.of(
            Column.NAME, "Nom produit",
            Column.QUANTITY, "Quantité",
            Column.UNIT, "Unité",
            Column.PRICE, "Prix",
            Column.COST, "Prix d'achat",
            Column.BARCODE, "Code barres",
            Column.CATEGORY, "Catégorie");

    private static final Map<Column, String> HELP = Map.of(
            Column.NAME, "Obligatoire. Le nom du produit tel qu'il apparaîtra en caisse.",
            Column.QUANTITY, "Quantité à ajouter au stock. Vide = 0.",
            Column.UNIT, "Unité de vente : pièce, kg, L, sac, bouteille... Facultatif.",
            Column.PRICE, "Prix de vente unitaire en Ariary. S'il manque, il sera demandé avant l'import.",
            Column.COST, "Prix d'achat unitaire en Ariary, chez le fournisseur. Facultatif, mais "
                    + "sans lui la marge de ces unités ne peut pas être calculée.",
            Column.BARCODE, "Code EAN du produit. Facultatif. La colonne est en format texte : "
                    + "ne la changez pas, sinon les codes longs sont abîmés.",
            Column.CATEGORY, "Rayon, avec ses niveaux séparés par « > » : Épicerie > Riz. "
                    + "Les catégories inconnues sont créées.");

    /** The extra unit the model shows, numbered like the first one a shop would add. */
    private static final int MODEL_UNIT = ProductImportColumns.FIRST_EXTRA_UNIT;

    private static final Map<UnitField, String> UNIT_HEADINGS = Map.of(
            UnitField.LABEL, "Unité " + MODEL_UNIT,
            UnitField.FACTOR, "Contenance " + MODEL_UNIT,
            UnitField.PRICE, "Prix " + MODEL_UNIT,
            UnitField.BARCODE, "Code barres " + MODEL_UNIT);

    private static final Map<UnitField, String> UNIT_HELP = Map.of(
            UnitField.LABEL, "Une autre unité de vente du même produit : carton, sac 50 kg, kg... "
                    + "Facultatif. Pour une troisième, ajoutez « Unité 3 », « Contenance 3 », etc.",
            UnitField.FACTOR, "Combien d'unités de base (colonne « Unité ») elle contient : 10 pour un "
                    + "carton de 10, 3,5 pour 1 kg de riz compté en kapoka. Au plus 3 décimales.",
            UnitField.PRICE, "Prix de vente de cette unité en Ariary : le prix du carton entier.",
            UnitField.BARCODE, "Code EAN propre à cette unité (celui du carton). Facultatif.");

    /** The extra unit of each example line, in the same order; null for none. */
    private static final List<Map<UnitField, Object>> UNIT_EXAMPLES = List.of(
            Map.of(UnitField.LABEL, "carton de 10",
                    UnitField.FACTOR, 10,
                    UnitField.PRICE, 43000,
                    UnitField.BARCODE, "6111234567906"),
            Map.of(UnitField.LABEL, "pack de 6",
                    UnitField.FACTOR, 6,
                    UnitField.PRICE, 11500));

    /** One map per example line; a column left out is an empty cell. */
    private static final List<Map<Column, Object>> EXAMPLES = List.of(
            Map.of(Column.NAME, "Riz Makalioka 1kg",
                    Column.QUANTITY, 50,
                    Column.UNIT, "sac",
                    Column.PRICE, 4500,
                    Column.COST, 3600,
                    Column.BARCODE, "6111234567890",
                    Column.CATEGORY, "Épicerie > Riz"),
            Map.of(Column.NAME, "Eau Vive 1,5L",
                    Column.QUANTITY, 24,
                    Column.UNIT, "bouteille",
                    Column.PRICE, 2000,
                    Column.COST, 1500,
                    Column.CATEGORY, "Boissons > Eau"));

    private ProductImportTemplate() {
    }

    /** The heading the model file writes for a column. */
    public static String heading(Column column) {
        return HEADINGS.getOrDefault(column, column.label());
    }

    public static byte[] xlsx() {
        List<Map<String, Object>> products = new ArrayList<>();
        for (int i = 0; i < EXAMPLES.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Column column : Column.values()) {
                row.put(heading(column), EXAMPLES.get(i).get(column));
            }
            for (UnitField field : UnitField.values()) {
                row.put(UNIT_HEADINGS.get(field), UNIT_EXAMPLES.get(i).get(field));
            }
            products.add(row);
        }

        List<Map<String, Object>> help = new ArrayList<>();
        for (Column column : Column.values()) {
            help.add(helpRow(heading(column), HELP.get(column)));
        }
        for (UnitField field : UnitField.values()) {
            help.add(helpRow(UNIT_HEADINGS.get(field), UNIT_HELP.get(field)));
        }
        help.add(helpRow("Remarque", "Remplacez les lignes d'exemple par vos produits. Seule la "
                + "première feuille est lue, au maximum " + CsvTable.MAX_ROWS + " lignes par import."));
        help.add(helpRow("Remarque", "Un produit déjà connu (même nom ou même code barres) voit son "
                + "stock complété au lieu d'être créé une deuxième fois : l'aperçu le montre avant l'import."));

        Map<String, List<Map<String, Object>>> sheets = new LinkedHashMap<>();
        sheets.put("Produits", products);
        sheets.put("Mode d'emploi", help);
        return ExcelUtils.toBytes(sheets);
    }

    private static Map<String, Object> helpRow(String column, String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("Colonne", column);
        row.put("Contenu", content);
        return row;
    }
}
