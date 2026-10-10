import { InvoiceRef } from './invoice.model';
import { Product } from './product.model';
import { UserApp } from './user-app.model';

/**
 * The concrete rows of the single stock_movement table
 * (@DiscriminatorColumn movement_type). The server exposes them through separate
 * endpoints, so the discriminator itself never has to travel.
 */
interface StockMovementBase {
  id: number;
  quantity: number;
  /** LocalDateTime serialized as ISO-8601. */
  movementDate: string;
  product: Product;
  performedBy: UserApp;
}

/**
 * Mirrors com.houssen.liberoshop.entity.Supply — goods coming in.
 *
 * <p>`supplier` is null exactly when the entry came from a product import: nobody delivered
 * those goods, they were counted in. A receipt booked at the depot always names one. Screens
 * label that case rather than showing a blank — "nobody delivered this" is information, an
 * empty cell looks like missing data.
 */
export interface Supply extends StockMovementBase {
  /** The same goods as received: 10 for "10 sacs" (`quantity` is in base units: 1 750). */
  receivedQuantity: number;
  /** The unit they came in — "sac 50 kg" — or null for the base unit. */
  unitLabel: string | null;
  supplier: { id: number; name: string } | null;
  /** Said outright by the server so a screen does not have to infer it from the null. */
  fromImport: boolean;
  /** Purchase price of one unit as received — the sack's — or null when it was not given. */
  unitCost: number | null;
  /** unitCost × receivedQuantity, the supplier invoice's line, or null with it. */
  totalCost: number | null;
}

/** Mirrors com.houssen.liberoshop.entity.AdjustmentReason. */
export type AdjustmentReason = 'BREAKAGE' | 'THEFT' | 'EXPIRED' | 'INVENTORY_COUNT' | 'OTHER';

/** The reasons as the screens name them, in the order they are offered. */
export const ADJUSTMENT_REASONS: readonly { value: AdjustmentReason; label: string }[] = [
  { value: 'INVENTORY_COUNT', label: 'Inventaire (rayon compté)' },
  { value: 'BREAKAGE', label: 'Casse' },
  { value: 'EXPIRED', label: 'Périmé ou abîmé' },
  { value: 'THEFT', label: 'Vol ou manquant' },
  { value: 'OTHER', label: 'Autre perte' },
];

/** Mirrors com.houssen.liberoshop.entity.StockAdjustment — the stock corrected by hand. */
export interface StockAdjustment extends StockMovementBase {
  /** Signed, unlike the other movements: −3 for three bottles broken, +2 found by a count. */
  quantity: number;
  reason: AdjustmentReason;
  comment: string | null;
  /** `quantity` at the average purchase cost, signed like it; null while the product has none. */
  costValue: number | null;
}

/** Mirrors CreateStockAdjustmentRequest. */
export interface CreateStockAdjustmentRequest {
  productId: number;
  reason: AdjustmentReason;
  /** What was counted for an inventory, what was lost for every other reason — in base units. */
  quantity: number;
  comment: string | null;
}

/** Mirrors com.houssen.liberoshop.entity.StockOutput — goods leaving against an invoice. */
export interface StockOutput extends StockMovementBase {
  invoice: InvoiceRef;
  /** unit price × quantity, precomputed server-side. */
  value: number;
}
