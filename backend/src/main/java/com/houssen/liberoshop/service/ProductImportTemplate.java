package com.houssen.liberoshop.service;

import com.houssen.liberoshop.service.ProductImportColumns.Column;
import com.houssen.liberoshop.util.ExcelUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The model workbook offered next to the import button: the products sheet with example lines,
 * the "Unités" sheet that declares the units those lines may be written in, and a sheet saying
 * what each column takes.
 *
 * <p>The examples show what the two sheets are for together: the same rice received as "2
 * tonne" and "200 kapoka" on two lines, converted by the import with the units the second sheet
 * declares -- nobody computes 7 200 kapoka by hand. A product sold one way only has no row in
 * "Unités" and is written as it always was.
 *
 * <p>The headers are the columns of {@link ProductImportColumns} and {@link ProductUnitSheet}
 * written the way a person would type them; every one of them folds onto its column's first
 * alias, which the tests check by reading the model back through {@link SpreadsheetReader}.
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
            Column.NAME, "Obligatoire. Le nom du produit tel qu'il apparaîtra en caisse. Le même "
                    + "produit peut revenir sur plusieurs lignes : les quantités s'additionnent.",
            Column.QUANTITY, "Quantité reçue, dans l'unité de la ligne. Vide = 0.",
            Column.UNIT, "L'unité de la quantité : pièce, kg, tonne, carton... Pour un produit de la "
                    + "feuille « " + ProductUnitSheet.SHEET_NAME + " », n'importe laquelle de ses unités : "
                    + "la quantité est convertie toute seule. Sinon, c'est l'unité dans laquelle "
                    + "le stock est compté.",
            Column.PRICE, "Prix de vente de l'unité de base, en Ariary. Pour un produit de la feuille « "
                    + ProductUnitSheet.SHEET_NAME + " », laissez vide : les prix y sont. S'il manque "
                    + "partout, il sera demandé avant l'import.",
            Column.COST, "Prix d'achat d'une unité de la ligne (d'une tonne pour « 2 tonne »), en "
                    + "Ariary. Facultatif, mais sans lui la marge ne peut pas être calculée.",
            Column.BARCODE, "Code EAN du produit. Facultatif. La colonne est en format texte : "
                    + "ne la changez pas, sinon les codes longs sont abîmés.",
            Column.CATEGORY, "Rayon, avec ses niveaux séparés par « > » : Épicerie > Riz. "
                    + "Les catégories inconnues sont créées.");

    /** One map per example line; a column left out is an empty cell. */
    private static final List<Map<Column, Object>> EXAMPLES = List.of(
            Map.of(Column.NAME, "Riz vrac Makalioka",
                    Column.QUANTITY, 2,
                    Column.UNIT, "tonne",
                    Column.COST, 2450000,
                    Column.CATEGORY, "Épicerie > Riz"),
            Map.of(Column.NAME, "Riz vrac Makalioka",
                    Column.QUANTITY, 200,
                    Column.UNIT, "kapoka",
                    Column.COST, 700,
                    Column.CATEGORY, "Épicerie > Riz"),
            Map.of(Column.NAME, "Eau Vive 1,5L",
                    Column.QUANTITY, 10,
                    Column.UNIT, "pack de 6",
                    Column.COST, 9000,
                    Column.BARCODE, "6111234567890",
                    Column.CATEGORY, "Boissons > Eau"),
            Map.of(Column.NAME, "Savon de Marseille",
                    Column.QUANTITY, 48,
                    Column.UNIT, "pièce",
                    Column.PRICE, 1500,
                    Column.COST, 1100,
                    Column.CATEGORY, "Hygiène"));

    private static final Map<ProductUnitSheet.Column, String> UNIT_HEADINGS = Map.of(
            ProductUnitSheet.Column.PRODUCT, "Produit",
            ProductUnitSheet.Column.UNIT, "Unité",
            ProductUnitSheet.Column.FACTOR, "Contenance",
            ProductUnitSheet.Column.PRICE, "Prix",
            ProductUnitSheet.Column.BARCODE, "Code barres");

    private static final Map<ProductUnitSheet.Column, String> UNIT_HELP = Map.of(
            ProductUnitSheet.Column.PRODUCT, "Le nom du produit, écrit comme dans la feuille Produits "
                    + "(ou comme au catalogue).",
            ProductUnitSheet.Column.UNIT, "Une unité dans laquelle le produit se vend ou se reçoit.",
            ProductUnitSheet.Column.FACTOR, "Combien d'unités de base elle contient. L'unité de base "
                    + "(celle du stock, la plus petite) a la contenance 1 : kapoka 1, kg 3,5, "
                    + "tonne 3500. Au plus 3 décimales.",
            ProductUnitSheet.Column.PRICE, "Prix de vente de cette unité entière, en Ariary : le prix "
                    + "du sac, pas du kilo.",
            ProductUnitSheet.Column.BARCODE, "Code EAN propre à cette unité (celui du sac). Facultatif.");

    /** The units of the examples above: one row per unit, the base one at contenance 1. */
    private static final List<Map<ProductUnitSheet.Column, Object>> UNIT_EXAMPLES = List.of(
            unitRow("Riz vrac Makalioka", "kapoka", 1, 900, null),
            unitRow("Riz vrac Makalioka", "kg", 3.5, 3000, null),
            unitRow("Riz vrac Makalioka", "sac 50 kg", 175, 145000, "6111234567906"),
            unitRow("Riz vrac Makalioka", "tonne", 3500, 2800000, null),
            unitRow("Eau Vive 1,5L", "bouteille", 1, 2000, null),
            unitRow("Eau Vive 1,5L", "pack de 6", 6, 11500, null));

    private ProductImportTemplate() {
    }

    /** The heading the model file writes for a column. */
    public static String heading(Column column) {
        return HEADINGS.getOrDefault(column, column.label());
    }

    /** The heading the model file writes for a column of the "Unités" sheet. */
    public static String heading(ProductUnitSheet.Column column) {
        return UNIT_HEADINGS.getOrDefault(column, column.label());
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

        List<Map<String, Object>> units = new ArrayList<>();
        for (Map<ProductUnitSheet.Column, Object> example : UNIT_EXAMPLES) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (ProductUnitSheet.Column column : ProductUnitSheet.Column.values()) {
                row.put(heading(column), example.get(column));
            }
            units.add(row);
        }

        List<Map<String, Object>> help = new ArrayList<>();
        for (Column column : Column.values()) {
            help.add(helpRow(heading(column), HELP.get(column)));
        }
        for (ProductUnitSheet.Column column : ProductUnitSheet.Column.values()) {
            help.add(helpRow(ProductUnitSheet.SHEET_NAME + " : " + heading(column), UNIT_HELP.get(column)));
        }
        help.add(helpRow("Remarque", "Remplacez les lignes d'exemple par vos produits, au maximum "
                + CsvTable.MAX_ROWS + " lignes par import."));
        help.add(helpRow("Remarque", "La feuille « " + ProductUnitSheet.SHEET_NAME + " » est facultative. "
                + "Elle se remplit une fois par produit ; ensuite chaque livraison s'écrit dans "
                + "son unité (2 tonne, 200 kapoka) sans rien calculer."));
        help.add(helpRow("Remarque", "Un produit déjà connu (même nom ou même code barres) voit son "
                + "stock complété au lieu d'être créé une deuxième fois, et reçoit les unités qui "
                + "lui manquent : l'aperçu le montre avant l'import."));

        Map<String, List<Map<String, Object>>> sheets = new LinkedHashMap<>();
        sheets.put("Produits", products);
        sheets.put(ProductUnitSheet.SHEET_NAME, units);
        sheets.put("Mode d'emploi", help);
        return ExcelUtils.toBytes(sheets);
    }

    private static Map<ProductUnitSheet.Column, Object> unitRow(String product, String unit,
                                                                Number factor, Number price,
                                                                String barcode) {
        Map<ProductUnitSheet.Column, Object> row = new LinkedHashMap<>();
        row.put(ProductUnitSheet.Column.PRODUCT, product);
        row.put(ProductUnitSheet.Column.UNIT, unit);
        row.put(ProductUnitSheet.Column.FACTOR, factor);
        row.put(ProductUnitSheet.Column.PRICE, price);
        row.put(ProductUnitSheet.Column.BARCODE, barcode);
        return row;
    }

    private static Map<String, Object> helpRow(String column, String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("Colonne", column);
        row.put("Contenu", content);
        return row;
    }
}
