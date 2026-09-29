/**
 * Purchase costs and margins — served by /api/costing, never to the counter.
 *
 * <p>The method is the weighted average cost ("coût moyen pondéré"), kept by the server: each
 * receipt that states a unit cost blends into the product's average, and each sale freezes the
 * average of that moment on its line. Nothing here is computed in the browser.
 */

/** GET /api/costing/products — mirrors ProductCostResponse. */
export interface ProductCost {
  productId: number;
  name: string;
  unit: string | null;
  salePrice: number;
  stockQuantity: number;
  /** Weighted average cost of the stock on hand; null while no receipt has stated one. */
  averageCost: number | null;
  /** salePrice − averageCost, or null with it. */
  unitMargin: number | null;
  /** unitMargin as a percentage of the sale price, one decimal. */
  marginRate: number | null;
  /** What the latest costed receipt paid — what the next one most likely pays. */
  lastUnitCost: number | null;
  lastSupplierName: string | null;
  lastPurchaseDate: string | null;
}

/** GET /api/costing/products/{id}/suppliers — mirrors SupplierPriceResponse. */
export interface SupplierPrice {
  /** Null for the entries an import wrote without a supplier. */
  supplierId: number | null;
  supplierName: string;
  receipts: number;
  units: number;
  /** Weighted by the units of each receipt. */
  averageCost: number;
  lowestCost: number;
  highestCost: number;
  lastUnitCost: number;
  lastPurchaseDate: string;
}

/** One product's line of a margin report. */
export interface ProductMargin {
  productId: number;
  name: string;
  unitsSold: number;
  revenue: number;
  costOfGoodsSold: number;
  grossMargin: number;
  marginRate: number | null;
  /** False when some of its sales had no known cost; the margin then covers the others. */
  fullyCosted: boolean;
}

/** GET /api/costing/margin — mirrors MarginReportResponse. */
export interface MarginReport {
  /** ISO dates, both inclusive. */
  from: string;
  to: string;
  /** Everything sold in the period, paid or not. */
  revenue: number;
  /** The part of revenue whose cost is known. */
  costedRevenue: number;
  costOfGoodsSold: number;
  grossMargin: number;
  marginRate: number | null;
  /** Sold without a known cost: reported, never counted as profit. */
  uncostedRevenue: number;
  /** Share of revenue the margin speaks for, 0–100. */
  coveragePercent: number;
  byProduct: ProductMargin[];
}
