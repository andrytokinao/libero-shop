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

/** Mirrors com.houssen.libertyshop.entity.Supply — goods coming in. */
export interface Supply extends StockMovementBase {
  supplier: { id: number; name: string };
}

/** Mirrors com.houssen.libertyshop.entity.StockOutput — goods leaving against an invoice. */
export interface StockOutput extends StockMovementBase {
  invoice: InvoiceRef;
  /** unit price × quantity, precomputed server-side. */
  value: number;
}
