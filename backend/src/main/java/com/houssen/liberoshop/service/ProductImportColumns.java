package com.houssen.liberoshop.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The columns a product import file may carry, and how they are recognised.
 *
 * <p>Nothing is positional and nothing is required to be spelled one way. A shopkeeper exports
 * their stock from a spreadsheet somebody else set up; the header says "Désignation" or
 * "Article" or "Nom du produit", the quantity column sits wherever it sits, and asking them to
 * rewrite the file to a template is asking them not to use the feature. So each column is a
 * list of spellings, folded through {@link CsvTable#normalise} -- which drops accents, case and
 * punctuation -- and the first one found wins.
 *
 * <p>The first alias of each column is its documented name: it is what the UI recommends and
 * what {@link Mapping#missing()} names when the file has none of them.
 *
 * <p>Only {@link Column#NAME} is truly needed. A file with nothing but a column of product
 * names is a catalogue with no stock and no prices, which is a legitimate thing to import
 * before a stock count -- the preview then asks for the rest.
 */
public final class ProductImportColumns {

    /** One recommended column, with every spelling accepted for it. */
    public enum Column {

        NAME("nom produit", "nom", "produit", "designation", "libelle", "article",
                "nom du produit", "product name", "name", "description"),

        QUANTITY("quantite", "qte", "quantity", "qty", "stock", "quantite stock",
                "qte stock", "stock qte", "stock initial", "nombre", "nb"),

        UNIT("unite", "unit", "mesure", "conditionnement", "uom", "u"),

        PRICE("prix", "prix unitaire", "pu", "prix de vente", "prix vente", "pv",
                "price", "unit price", "tarif"),

        BARCODE("code barres", "code barre", "codes barres", "barcode", "ean", "ean13",
                "code ean", "gencod", "gencode"),

        CATEGORY("categorie", "rayon", "famille", "category", "sous famille",
                "categorie produit", "groupe");

        private final List<String> aliases;

        Column(String... aliases) {
            this.aliases = List.of(aliases);
        }

        /** The spelling the documentation and the UI recommend. */
        public String label() {
            return aliases.getFirst();
        }

        /** Every accepted spelling, folded the way headers are folded. */
        String[] normalisedAliases() {
            return aliases.stream().map(CsvTable::normalise).toArray(String[]::new);
        }
    }

    private ProductImportColumns() {
    }

    /**
     * Which column of the file is which, plus what the operator should be told about the
     * header line.
     *
     * @param indexes    where each recognised column landed
     * @param recognised the header the file actually wrote, per column found -- so the dialog
     *                   can show "Quantité → « Qte stock »" and the operator can confirm the
     *                   guess rather than trust it
     * @param ignored    headers that matched no column. A misspelled "quantit" is read as no
     *                   quantity at all, and silently importing 300 products at stock 0 would
     *                   be worse than saying which header was skipped.
     * @param missing    recommended columns the file has none of, under their documented names
     */
    public record Mapping(Map<Column, Integer> indexes,
                          Map<String, String> recognised,
                          List<String> ignored,
                          List<String> missing) {

        public boolean has(Column column) {
            return indexes.containsKey(column);
        }

        /** The trimmed cell for that column, or {@code ""} when the file has no such column. */
        public String cell(CsvTable.Row row, Column column) {
            Integer index = indexes.get(column);
            return index == null ? "" : row.cell(index);
        }
    }

    public static Mapping map(CsvTable table) {
        Map<Column, Integer> indexes = new EnumMap<>(Column.class);
        Map<String, String> recognised = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        for (Column column : Column.values()) {
            int index = table.indexOf(column.normalisedAliases());
            if (index >= 0) {
                indexes.put(column, index);
                recognised.put(column.label(), table.headers().get(index).trim());
            } else {
                missing.add(column.label());
            }
        }

        List<String> ignored = new ArrayList<>();
        for (int i = 0; i < table.headers().size(); i++) {
            String header = table.headers().get(i).trim();
            if (!header.isEmpty() && !indexes.containsValue(i)) {
                ignored.add(header);
            }
        }

        return new Mapping(Map.copyOf(indexes), Map.copyOf(recognised),
                List.copyOf(ignored), List.copyOf(missing));
    }

    /**
     * A number as a spreadsheet wrote it, or null when the cell holds nothing usable.
     *
     * <p>Three habits have to survive this. Thousands are grouped, with a space, a
     * non-breaking space or a dot -- "12 500", "12.500". The decimal mark is a comma here, a
     * dot elsewhere. And the cell often carries its currency: "12 500 Ar", "12500 MGA".
     *
     * <p>The one genuinely ambiguous case is a lone dot: "1.500" is fifteen hundred to a
     * French spreadsheet and one-and-a-half to an English one. It is read as a thousands
     * separator when exactly three digits follow it, and as a decimal point otherwise, which
     * gets "1.500" right as 1500 and "2.50" right as 2.5. A price of one-and-a-half Ariary
     * does not exist, and that is what makes the trade safe here.
     */
    public static BigDecimal number(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[^0-9.,\\-]", "");
        if (cleaned.isEmpty() || cleaned.equals("-")) {
            return null;
        }

        int lastComma = cleaned.lastIndexOf(',');
        int lastDot = cleaned.lastIndexOf('.');
        String normalised;
        if (lastComma >= 0 && lastDot >= 0) {
            // Both present: whichever comes last is the decimal mark, the other groups.
            char decimal = lastComma > lastDot ? ',' : '.';
            char grouping = decimal == ',' ? '.' : ',';
            normalised = cleaned.replace(String.valueOf(grouping), "").replace(decimal, '.');
        } else if (lastComma >= 0) {
            normalised = cleaned.replace(',', '.');
        } else if (lastDot >= 0 && cleaned.length() - lastDot == 4) {
            normalised = cleaned.replace(".", "");
        } else {
            normalised = cleaned;
        }

        try {
            return new BigDecimal(normalised);
        } catch (NumberFormatException e) {
            // "12-34", "1.2.3": a cell nobody can read as one number. The caller notes it.
            return null;
        }
    }
}
