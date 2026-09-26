/** Mirrors com.houssen.liberoshop.entity.Category (table category), as nested in a Product. */
export interface Category {
  id: number;
  /** Unique across the whole tree, not only among siblings — see CategoryNode. */
  name: string;
}

/**
 * A rayon as `GET /api/categories` serves it: the tree flattened, depth-first.
 *
 * <p>Flat rather than nested because every screen that shows rayons shows them as rows — a
 * `<select>`, a table, a column of boxes — and a flat list carrying `depth` indents in one
 * line of template, where nested children would need a recursive component for each of them.
 * The order is already the tree's: a parent is immediately followed by its children.
 */
export interface CategoryNode extends Category {
  /** The wider rayon, or null for a root. */
  parentId: number | null;
  /** 0 for a root, 1 for its children. What the UI indents by. */
  depth: number;
  /** Ancestors and name, e.g. `Boissons > Eau` — shown where a bare name would be ambiguous. */
  path: string;
  /** How many rayons sit directly inside this one, so a leaf reads as a leaf. */
  children: number;
}

/** POST /api/categories. `parentId` null creates a root. */
export interface CreateCategoryRequest {
  name: string;
  parentId: number | null;
}

/**
 * PUT /api/categories/{id} — the name and where it hangs.
 *
 * <p>A full replacement: `parentId: null` moves the rayon out to the top level rather than
 * leaving it where it was, which is the only way to promote a child to a root.
 */
export interface UpdateCategoryRequest {
  name: string;
  parentId: number | null;
}

/** DELETE /api/categories/{id} — how many references changed rayon on the way out. */
export interface DeletedCategory {
  id: number;
  productsMoved: number;
}

/**
 * Mirrors CategoryService.MAX_DEPTH. Used only to grey out an impossible parent before the
 * round trip; the server refuses a deeper one regardless.
 */
export const MAX_CATEGORY_DEPTH = 4;

/** How a path is written on both sides — mirrors CategoryService.PATH_SEPARATOR. */
export const CATEGORY_PATH_SEPARATOR = ' > ';
