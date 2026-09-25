import { PaymentMethod } from './enums';
import { InvoiceRef } from './invoice.model';
import { UserApp } from './user-app.model';

/**
 * Mirrors com.houssen.libertyshop.entity.Payment (table payment), as served by
 * PaymentResponse.
 */
export interface Payment {
  id: number;
  amount: number;
  paymentMethod: PaymentMethod;
  /** LocalDateTime serialized as ISO-8601. */
  paymentDate: string;
  invoice: InvoiceRef;
  collectedBy: UserApp;
  /** Null while the collector still physically holds the cash. */
  cashRemittanceId: number | null;
}
