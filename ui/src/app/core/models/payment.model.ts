import { CashRemittance } from './cash-remittance.model';
import { PaymentMethod } from './enums';
import { Invoice } from './invoice.model';
import { UserApp } from './user-app.model';

/** Mirrors com.houssen.libertyshop.entity.Payment (table payment). */
export interface Payment {
  id: number;
  /** BigDecimal(12,2) on the backend. */
  amount: number;
  paymentMethod: PaymentMethod;
  /** LocalDateTime serialized as ISO-8601. */
  paymentDate: string;
  invoice: Invoice;
  collectedBy: UserApp;
  /** Null while the cash is still in the collector's hands. */
  cashRemittance: CashRemittance | null;
}
