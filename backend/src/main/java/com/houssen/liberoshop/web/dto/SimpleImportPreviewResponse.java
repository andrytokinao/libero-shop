package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.service.ImportAction;
import com.houssen.liberoshop.service.ImportOutcome;

import java.util.List;
import java.util.Map;

/**
 * An import file of something that is only a few text columns wide, read and not yet applied.
 *
 * <p>Suppliers and rayons share this shape, and products deliberately do not. A product line
 * carries decisions nothing else has -- top up a stock or add a second reference, a price the
 * file usually omits, a rayon to place it in -- and flattening those into a bag of strings would
 * cost the very type safety that makes {@code ProductImportLineResponse} readable. What is left
 * over, once you take those away, really is "a name and two other fields", twice over. So the
 * values travel as a map and the shape of that map travels with them, in {@code fields}.
 *
 * <p>That is what lets one dialog in the browser serve both: it renders a column per field spec
 * rather than a column per hard-coded name, and adding a third resource of this kind later needs
 * no new screen at all.
 *
 * @param resource  what is being imported, {@code "categories"} or {@code "suppliers"} -- the UI
 *                  uses it for wording, never for behaviour
 * @param fields    the columns, in the order they should be shown; also the keys of every line's
 *                  {@code values}
 * @param merged    lines that would complete something already recorded rather than add to it
 */
public record SimpleImportPreviewResponse(String resource,
                                          String separator,
                                          int total,
                                          int created,
                                          int merged,
                                          int skipped,
                                          List<ImportFieldSpec> fields,
                                          List<String> recognised,
                                          List<String> ignored,
                                          List<String> missing,
                                          List<Line> lines) {

    /**
     * One column of the file, as the dialog should render it.
     *
     * @param key       how the value is keyed in {@code Line.values}
     * @param label     the recommended header, and the column heading shown
     * @param required  a line with this value blank cannot be imported
     * @param maxLength what the column holds; the input is capped at it rather than the server
     *                  truncating silently
     */
    public record ImportFieldSpec(String key, String label, boolean required, int maxLength) {
    }

    /**
     * @param values       keyed by {@link ImportFieldSpec#key()}; a key the file had no column for
     *                     is present and empty, so the dialog always has a cell to offer
     * @param existingId   what it was matched with, or null
     * @param existingLabel how that match reads, so the row can justify itself on screen
     */
    public record Line(int line,
                       Map<String, String> values,
                       ImportOutcome outcome,
                       ImportAction action,
                       Long existingId,
                       String existingLabel,
                       boolean selected,
                       List<String> notes) {
    }
}
