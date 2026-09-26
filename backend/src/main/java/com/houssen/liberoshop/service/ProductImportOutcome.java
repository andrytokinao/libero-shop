package com.houssen.liberoshop.service;

/** What became of one line of an import file. */
public enum ProductImportOutcome {

    /** No product carried that name: a new reference was added. */
    CREATED,

    /** The name was already in the catalogue: the quantities were added to it. */
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
