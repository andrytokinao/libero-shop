package com.houssen.liberoshop.web.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * One tile of the catalogue explorer: a rayon shown as a folder, or a product.
 *
 * <p>The Composite pattern, on the read side only. The entities stay what they are -- a rayon
 * holds rayons through its parent link, a product sits in a rayon -- and only the explorer needs
 * to treat both as entries of one grid. A sealed interface rather than a class hierarchy, so a
 * caller handles every kind there is, and a new kind is one more record here.
 *
 * <p>Serialized with a {@code kind} discriminator -- {@code "folder"} or {@code "product"} -- which
 * is what the screens switch on.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CatalogEntry.Folder.class, name = "folder"),
        @JsonSubTypes.Type(value = CatalogEntry.Item.class, name = "product")})
public sealed interface CatalogEntry permits CatalogEntry.Folder, CatalogEntry.Item {

    /**
     * A rayon as a folder.
     *
     * @param folders  rayons directly inside it
     * @param products products in it and in every rayon below it -- what opening it can lead to
     */
    record Folder(CategoryNodeResponse rayon, int folders, long products) implements CatalogEntry {
    }

    /** A product as a file of the folder it sits in. */
    record Item(ProductResponse product) implements CatalogEntry {
    }
}
