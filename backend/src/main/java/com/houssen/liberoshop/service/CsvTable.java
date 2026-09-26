package com.houssen.liberoshop.service;

import com.houssen.liberoshop.service.exception.BusinessRuleException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A delimited text file read as a header line plus rows.
 *
 * <p>The separator is worked out rather than configured. A shopkeeper exports a spreadsheet
 * and sends the file; asking them which character Excel chose would be asking them something
 * they cannot see. French Excel writes {@code ;}, everything else writes {@code ,}, and a
 * file pasted out of a text editor sometimes uses a tab -- the header line is counted and the
 * winner used for the whole file.
 *
 * <p>Columns are found by name, not by position, so the operator may reorder them, add their
 * own, and name them in French or in English. {@link #indexOf(String...)} takes the accepted
 * spellings and returns where the column landed.
 */
public final class CsvTable {

    /**
     * Enough for any shop's catalogue, and a ceiling on what a single paste can cost. The
     * whole file is held in memory twice over -- as text and as cells -- so this is not a
     * formality.
     */
    public static final int MAX_ROWS = 5_000;

    private static final char[] CANDIDATE_SEPARATORS = {';', ',', '\t', '|'};

    private final List<String> headers;
    /** Normalised header to column index, first occurrence winning. */
    private final Map<String, Integer> byHeader;
    private final List<Row> rows;
    private final char separator;

    private CsvTable(List<String> headers, List<Row> rows, char separator) {
        this.headers = headers;
        this.rows = rows;
        this.separator = separator;
        this.byHeader = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            byHeader.putIfAbsent(normalise(headers.get(i)), i);
        }
    }

    /**
     * @throws BusinessRuleException if the text holds no header line, or more rows than
     *                               {@link #MAX_ROWS} -- both are told to the operator as is
     */
    public static CsvTable parse(String content) {
        String text = stripBom(content == null ? "" : content);
        char separator = detectSeparator(text);
        List<Row> records = split(text, separator);

        if (records.isEmpty()) {
            throw new BusinessRuleException("IMPORT_EMPTY_FILE",
                    "Le fichier est vide : il faut au moins une ligne d'en-tete et une ligne de produit.");
        }
        Row header = records.getFirst();
        List<Row> body = records.subList(1, records.size());
        if (body.size() > MAX_ROWS) {
            throw new BusinessRuleException("IMPORT_TOO_MANY_ROWS",
                    "Le fichier contient " + body.size() + " lignes, le maximum est de " + MAX_ROWS
                            + ". Decoupez-le en plusieurs imports.");
        }
        return new CsvTable(header.cells(), List.copyOf(body), separator);
    }

    /** The separator that was used, so the preview can say which one the file turned out to be. */
    public char separator() {
        return separator;
    }

    public List<String> headers() {
        return headers;
    }

    public List<Row> rows() {
        return rows;
    }

    /**
     * Where a column landed, or {@code -1} when the file has none of its accepted spellings.
     *
     * @param aliases already normalised -- lower case, unaccented, letters and digits only
     */
    public int indexOf(String... aliases) {
        for (String alias : aliases) {
            Integer index = byHeader.get(alias);
            if (index != null) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Header and product names are folded the same way: accents dropped, case dropped, and
     * everything that is not a letter or a digit removed. It is what makes "Code-barres",
     * "code barres" and "CODE_BARRES" the same column.
     */
    public static String normalise(String value) {
        if (value == null) {
            return "";
        }
        String unaccented = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return unaccented.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    /**
     * One row of the file.
     *
     * @param line  1-based line number in the original text, quoted newlines counted, so a
     *              complaint names the line the operator sees in their editor
     * @param cells as many as the row actually had -- a short row is not padded, {@link
     *              #cell(int)} answers for the missing columns
     */
    public record Row(int line, List<String> cells) {

        /** The trimmed cell, or {@code ""} when the row stops before that column. */
        public String cell(int index) {
            if (index < 0 || index >= cells.size()) {
                return "";
            }
            return cells.get(index).trim();
        }

        public boolean isBlank() {
            return cells.stream().allMatch(String::isBlank);
        }
    }

    // ------------------------------------------------------------------ parsing

    private static String stripBom(String content) {
        return content.startsWith("﻿") ? content.substring(1) : content;
    }

    /**
     * The most frequent candidate on the header line wins, {@code ;} on a tie or a single
     * column -- a one-column file parses the same whatever we pick.
     */
    private static char detectSeparator(String text) {
        int end = text.indexOf('\n');
        String headerLine = end < 0 ? text : text.substring(0, end);
        char best = ';';
        int bestCount = 0;
        for (char candidate : CANDIDATE_SEPARATORS) {
            int count = (int) headerLine.chars().filter(c -> c == candidate).count();
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    /**
     * Splits into records, honouring quotes: a quoted field may hold the separator, and may
     * hold newlines -- which is why the line counter is kept inside the scan rather than
     * derived from a list of lines.
     *
     * <p>A quote only opens a field at its very start, so an unquoted {@code 33 cm"} keeps
     * its inch mark instead of swallowing the rest of the file.
     */
    private static List<Row> split(String text, char separator) {
        List<Row> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int recordLine = 1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    cell.append(c);
                }
                continue;
            }
            if (c == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (c == separator) {
                current.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                current.add(cell.toString());
                cell.setLength(0);
                addUnlessBlank(records, recordLine, current);
                current = new ArrayList<>();
                line++;
                recordLine = line;
            } else {
                cell.append(c);
            }
        }
        current.add(cell.toString());
        addUnlessBlank(records, recordLine, current);
        return records;
    }

    /**
     * Blank lines are dropped rather than reported: a spreadsheet export ends with one, and a
     * file where the products are spaced out is still a file of products.
     */
    private static void addUnlessBlank(List<Row> records, int line, List<String> cells) {
        Row row = new Row(line, List.copyOf(cells));
        if (!row.isBlank()) {
            records.add(row);
        }
    }
}
