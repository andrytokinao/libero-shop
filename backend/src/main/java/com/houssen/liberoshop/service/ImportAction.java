package com.houssen.liberoshop.service;

/** What the operator decided to do with one line of an import file. */
public enum ImportAction {

    /**
     * Fold this line into something already recorded. For a product, its quantity is added to
     * the existing stock; for a supplier, the fields the shop left blank are filled in. In
     * neither case does the file overwrite what the shop already wrote: it is read as a delivery
     * note, not as a re-description of things the shop already knows about.
     */
    MERGE,

    /**
     * Add a new row, even when something of that name exists. This is what the "Renommer" button
     * on a preview row produces -- two suppliers' "Lait 1L" really are two products, and the
     * operator is the only one who can tell.
     */
    CREATE
}
