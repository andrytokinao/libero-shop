import { Category } from './category.model';

/**
 * Every way a product is sold, as served by ProductUnitsResponse: the base unit, which the stock
 * is counted in, then the other units, smallest first.
 */
export interface ProductUnits {
  productId: number;
  productName: string;
  /** Null for a product counted in bare units. */
  baseUnit: string | null;
  basePrice: number;
  baseBarcode: string | null;
  stockQuantity: number;
  /** The rayon it is filed under, or null for none. */
  category: Category | null;
  packagings: Packaging[];
  /** The packaging the shop buys it in — "tonne" — or null for the base unit. */
  purchaseUnitId: number | null;
}

/** Another unit than the base one: "kg = 3.5 kapoka, 3 000 Ar", "sac 25 kg = 25 kg". */
export interface Packaging {
  id: number;
  label: string;
  /** How many base units one of these holds, worked out by the server: 87.5 for the sack. */
  factor: number;
  price: number;
  barcode: string | null;
  /** The unit it is written in — "kg" for the sack — or null for the base unit. */
  reference: string | null;
  /** How many of `reference` it holds: 25. */
  referenceQuantity: number;
}

/** A product's sheet as the dialog sends it — mirrors UpdateProductRequest. */
export interface UpdateProductRequest {
  name: string;
  /** Of one base unit. */
  price: number;
  barcode: string | null;
  /** The rayon, or null for none: the dialog always sends the one it shows. */
  categoryId: number | null;
  /**
   * Purchase cost of one `purchaseUnitId` — a tonne for rice bought by the tonne — corrected by
   * hand; null leaves it as the receipts made it.
   */
  purchaseCost: number | null;
  /** The unit the shop buys it in, or null for the base unit: the dialog sends the one it shows. */
  purchaseUnitId: number | null;
}

/** A product typed in by hand — mirrors CreateProductRequest. */
export interface CreateProductRequest {
  name: string;
  /** Of one base unit. */
  price: number;
  /** How the shelf counts it; null for bare units. */
  unit: string | null;
  barcode: string | null;
  categoryId: number | null;
  /** What one base unit costs the shop, or null when it is not known yet. */
  purchaseCost: number | null;
  /** Already on the shelf, in base units; booked as an entry without a supplier. */
  initialStock: number | null;
}

/** A unit as the dialog sends it, for a creation and an edit alike. */
export interface PackagingRequest {
  label: string;
  /** How many `reference` units it holds — the contenance, as printed on it. */
  factor: number;
  price: number;
  barcode: string | null;
  /** The unit it is written in, by name; null or absent for the base unit. */
  reference?: string | null;
}
