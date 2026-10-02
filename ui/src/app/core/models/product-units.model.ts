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

/** Another unit than the base one: "kg = 3.5 kapoka, 3 000 Ar". */
export interface Packaging {
  id: number;
  label: string;
  /** How many base units one of these holds. BigDecimal(12,3) on the backend. */
  factor: number;
  price: number;
  barcode: string | null;
}

/** A unit as the dialog sends it, for a creation and an edit alike. */
export interface PackagingRequest {
  label: string;
  factor: number;
  price: number;
  barcode: string | null;
}
