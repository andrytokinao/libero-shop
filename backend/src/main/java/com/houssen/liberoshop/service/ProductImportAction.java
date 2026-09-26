package com.houssen.liberoshop.service;

/** What the operator decided to do with one line of an import file. */
public enum ProductImportAction {

    /**
     * Add this line's quantity to a product already in the catalogue. The existing name,
     * price and rayon are kept: the file is a delivery note, not a re-description of goods
     * the shop already sells.
     */
    MERGE,

    /**
     * Add a new reference, even if a product of that name exists. This is what the "Renommer"
     * button on a preview row produces -- two suppliers' "Lait 1L" really are two products,
     * and the operator is the only one who can tell.
     */
    CREATE
}
