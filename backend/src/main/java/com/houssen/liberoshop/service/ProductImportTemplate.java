package com.houssen.liberoshop.service;

import com.houssen.liberoshop.service.ProductImportColumns.Column;
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
            Column.BARCODE, "Code barres",
            Column.CATEGORY, "Catégorie");

    private static final Map<Column, String> HELP = Map.of(
            Column.NAME, "Obligatoire. Le nom du produit tel qu'il apparaîtra en caisse.",
            Column.QUANTITY, "Quantité à ajouter au stock. Vide = 0.",
            Column.UNIT, "Unité de vente : pièce, kg, L, sac, bouteille... Facultatif.",
            Column.PRICE, "Prix de vente unitaire en Ariary. S'il manque, il sera demandé avant l'import.",
            Column.BARCODE, "Code EAN du produit. Facultatif. La colonne est en format texte : "
                    + "ne la changez pas, sinon les codes longs sont abîmés.",
            Column.CATEGORY, "Rayon, avec ses niveaux séparés par « > » : Épicerie > Riz. "
                    + "Les catégories inconnues sont créées.");

    /** One map per example line; a column left out is an empty cell. */
    private static final List<Map<Column, Object>> EXAMPLES = List.of(
            Map.of(Column.NAME, "Riz Makalioka 1kg",
                    Column.QUANTITY, 50,
                    Column.UNIT, "sac",
                    Column.PRICE, 4500,
                    Column.BARCODE, "6111234567890",
                    Column.CATEGORY, "Épicerie > Riz"),
            Map.of(Column.NAME, "Eau Vive 1,5L",
                    Column.QUANTITY, 24,
                    Column.UNIT, "bouteille",
                    Column.PRICE, 2000,
                    Column.CATEGORY, "Boissons > Eau"));

    private ProductImportTemplate() {
    }

    /** The heading the model file writes for a column. */
    public static String heading(Column column) {
        return HEADINGS.getOrDefault(column, column.label());
    }

    public static byte[] xlsx() {
        List<Map<String, Object>> products = new ArrayList<>();
        for (Map<Column, Object> example : EXAMPLES) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Column column : Column.values()) {
                row.put(heading(column), example.get(column));
            }
            products.add(row);
        }

        List<Map<String, Object>> help = new ArrayList<>();
        for (Column column : Column.values()) {
            help.add(helpRow(heading(column), HELP.get(column)));
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
