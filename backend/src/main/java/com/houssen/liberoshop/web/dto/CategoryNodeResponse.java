package com.houssen.liberoshop.web.dto;

/**
 * One rayon, with the three things a flat list cannot say for itself.
 *
 * <p>The tree is sent as a depth-first sequence rather than as nested objects: every screen
 * that shows categories shows them as rows -- a {@code <select>}, a table, a column of
 * checkboxes -- and a flat list carrying {@code depth} indents in one line of template,
 * where nested children would need a recursive component for each of them.
 *
 * @param parentId the wider rayon, or null for a root
 * @param depth    0 for a root, 1 for its children, and so on; what the UI indents by
 * @param path     the ancestors and the name, joined by {@code CategoryService.PATH_SEPARATOR}
 *                 -- shown where a row stands alone and "Eau" would not say which one
 * @param children how many rayons sit directly inside this one, so a screen can tell a leaf
 *                 from a branch without scanning the rest of the list
 */
public record CategoryNodeResponse(Long id,
                                   String name,
                                   Long parentId,
                                   int depth,
                                   String path,
                                   int children) {
}
