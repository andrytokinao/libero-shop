package com.houssen.liberoshop.service;

/**
 * What became of one line of an import file, whatever the file was about.
 *
 * <p>Shared by the product, rayon and supplier imports, because the four answers are the same
 * four every time: it was added, it was folded into something already there, it was added under
 * a freed name, or it was refused.
 */
public enum ImportOutcome {

    /** Nothing already recorded matched: a new row was added. */
    CREATED,

    /**
     * Something already recorded matched, and the line completed it rather than duplicating it.
     * For a product that means its stock rose; for a supplier, that a field the shop had left
     * blank was filled in. In neither case is anything the shop already wrote overwritten.
     */
    MERGED,

    /**
     * A new reference was added under a name the catalogue did not have free, so the name was
     * numbered -- "Lait 1L (2)".
     *
     * <p>Two situations lead here. In a preview, the line's name is taken by a product carrying
     * a different barcode: same wording, demonstrably not the same article, so it is proposed as
     * its own reference. At apply time, any {@code CREATE} whose name turns out to be taken --
     * because the operator kept it while renaming was the point, or because a colleague created
     * it in the meantime.
     */
    RENAMED,

    /** The line was refused, and {@code notes} says why. Nothing was written for it. */
    SKIPPED
}
