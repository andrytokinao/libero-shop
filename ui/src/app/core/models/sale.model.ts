import { PaymentStatus } from './enums';
import { Product } from './product.model';
import { UserApp } from './user-app.model';

/** Mirrors com.houssen.liberoshop.entity.SaleLine (table sale_line). */
export interface SaleLine {
  id: number;
  /** In the unit it was sold in — 2 for "2 kg" — to the thousandth. */
  quantity: number;
  /** The unit's name at the time of the sale; null for bare units. */
  unitLabel: string | null;
  /** Base units in one of the unit sold; 1 for the base unit. */
  unitFactor: number;
  /** BigDecimal(12,2) on the backend — price frozen at sale time. */
  unitPrice: number;
  product: Product;
}

/** Mirrors com.houssen.liberoshop.entity.Sale (table sale). */
export interface Sale {
  id: number;
  /** LocalDateTime serialized as ISO-8601. */
  saleDate: string;
  paymentStatus: PaymentStatus;
  totalAmount: number;
  seller: UserApp;
  lines: SaleLine[];
}

/** Front-end counterpart of Sale#calculateTotal(). */
export function calculateTotal(lines: readonly SaleLine[]): number {
  return lines.reduce((total, line) => total + line.unitPrice * line.quantity, 0);
}
