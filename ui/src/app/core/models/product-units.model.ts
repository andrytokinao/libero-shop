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
  packagings: Packaging[];
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

/** A product's name, base price and barcode as the dialog sends them — mirrors UpdateProductRequest. */
export interface UpdateProductRequest {
  name: string;
  /** Of one base unit. */
  price: number;
  barcode: string | null;
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
