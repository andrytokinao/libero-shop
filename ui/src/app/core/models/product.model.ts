import { Category } from './category.model';

/**
 * Mirrors com.houssen.liberoshop.entity.Product (table product), as served by
 * ProductResponse.
 */
export interface Product {
  id: number;
  name: string;
  /** BigDecimal(12,2) on the backend, sent as a JSON number. */
  price: number;
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
  /** price × stockQuantity, precomputed server-side. */
  stockValue: number;
}
