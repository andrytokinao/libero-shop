import { InvoiceRef } from './invoice.model';
import { Product } from './product.model';
import { UserApp } from './user-app.model';

/**
 * The two concrete rows of the single stock_movement table
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
  supplier: { id: number; name: string } | null;
  /** Said outright by the server so a screen does not have to infer it from the null. */
  fromImport: boolean;
  /** Purchase price of one unit of this receipt; null when it was not given. */
  unitCost: number | null;
  /** unitCost × quantity, or null with it. */
  totalCost: number | null;
}

/** Mirrors com.houssen.liberoshop.entity.StockOutput — goods leaving against an invoice. */
export interface StockOutput extends StockMovementBase {
  invoice: InvoiceRef;
  /** unit price × quantity, precomputed server-side. */
  value: number;
}
