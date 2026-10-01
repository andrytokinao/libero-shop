package com.houssen.liberoshop.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Text folded the way a cashier types it: no accents, no case, punctuation as a space.
 *
 * <p>"Café Malagasy 250g" is found by "cafe 250", "Coca-Cola 1,5 L" by "coca 1 5". The folding
 * is done once in Java, both on what is stored and on what is searched, so the database only
 * has to compare plain lower-case ASCII -- which H2, MySQL and PostgreSQL all do alike, where
 * their accent-insensitive collations do not.
 *
 * <p>Unlike {@code CsvTable.normalise}, which glues everything together to compare two
 * headers, words stay apart here: a search is made of words.
 */
public final class SearchText {

    private SearchText() {
    }

    /** The parts folded and joined by one space; null and blank parts are skipped. */
    public static String fold(String... parts) {
        return Arrays.stream(parts)
                .filter(Objects::nonNull)
                .map(SearchText::foldOne)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(" "));
    }

    /** The words of a query, folded; empty for a blank one. */
    public static List<String> words(String query) {
        String folded = fold(query);
        return folded.isEmpty() ? List.of() : List.of(folded.split(" "));
    }

    private static String foldOne(String value) {
        String unaccented = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return unaccented.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
