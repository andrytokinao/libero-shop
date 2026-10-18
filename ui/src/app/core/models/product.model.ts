import { Category, CategoryNode } from './category.model';
import { Packaging } from './product-units.model';

/**
 * Mirrors com.houssen.liberoshop.entity.Product (table product), as served by
 * ProductResponse.
 */
export interface Product {
  id: number;
  name: string;
  /** BigDecimal(12,2) on the backend, sent as a JSON number. */
  price: number;
  /** In the base unit (`unit`), to the thousandth: 1743.5 kapoka. */
  stockQuantity: number;
  /**
   * How the shelf counts it — "kg", "L", "sachet" — exactly as the operator wrote it. Null for
   * a catalogue counted in bare units, which the screens render as a dash rather than
   * inventing a word for.
   */
  unit: string | null;
  /** Unique, nullable — filled by hand today, by a barcode scanner tomorrow. */
  barcode: string | null;
  category: Category | null;
  /** Decided by the server's StockPolicy, so the threshold has one definition. */
  lowStock: boolean;
  /** The threshold it is judged by, in base units: its own or the shop's. Optional for old payloads. */
  lowStockThreshold?: number;
  /** No longer offered for sale; still in the stock lists. Optional for old payloads. */
  retired?: boolean;
  /** price × stockQuantity, precomputed server-side. */
  stockValue: number;
  /**
   * The other units it sells in — "kg", "sac 50 kg" — smallest first. Empty for most products,
   * and on the products nested in an invoice or a movement. Optional so a product kept in a held
   * sale from before units still reads.
   */
  packagings?: Packaging[];
}

/** One change to a product's sheet — mirrors CatalogChangeResponse. */
export interface CatalogChange {
  id: number;
  changedAt: string;
  /** Who made it; null for a change no one signed in for (an import at start-up). */
  changedBy: string | null;
  what: string;
  before: string | null;
  after: string | null;
}

/** One page of a browse — mirrors ProductPageResponse. */
export interface ProductPage {
  items: Product[];
  /** How many match in all, for "40 sur 130". */
  total: number;
  page: number;
  size: number;
  hasMore: boolean;
}

/**
 * One tile of the catalogue explorer — mirrors CatalogEntry: a rayon shown as a folder, or a
 * product. Switch on `kind`.
 */
export type CatalogEntry = CatalogFolder | CatalogItem;

/** A rayon as a folder: what opening it leads to. */
export interface CatalogFolder {
  kind: 'folder';
  rayon: CategoryNode;
  /** Rayons directly inside it. */
  folders: number;
  /** Products in it and in every rayon below it. */
  products: number;
}

export interface CatalogItem {
  kind: 'product';
  product: Product;
}

/** One page of the explorer: the folders (first page only), then a page of products. */
export interface CatalogPage {
  entries: CatalogEntry[];
  /** Products the open folder (or the search) holds in all, for "Afficher plus". */
  totalProducts: number;
  page: number;
  hasMore: boolean;
}

/** Where the explorer looks — mirrors the parameters of `GET /api/products/explore`. */
export interface CatalogExplore {
  q?: string;
  /** The open folder; absent for the top of the shop. */
  rayonId?: number | null;
  /** In a folder: every product below it, not only those filed directly in it. */
  all?: boolean;
  favorites?: boolean;
  page?: number;
  size?: number;
}

/** What a sale screen browses: words, a rayon and the ones below it, the seller's favourites. */
export interface ProductBrowse {
  q?: string;
  categoryId?: number | null;
  favorites?: boolean;
  page?: number;
  size?: number;
}
