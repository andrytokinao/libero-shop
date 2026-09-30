import { RemittanceStatus } from './enums';
import { UserApp } from './user-app.model';

/**
 * Mirrors com.houssen.liberoshop.entity.CashRemittance (table cash_remittance), as
 * served by CashRemittanceResponse.
 */
export interface CashRemittance {
  id: number;
  amount: number;
  /** LocalDateTime serialized as ISO-8601. */
  remittanceDate: string;
  status: RemittanceStatus;
  submittedBy: UserApp;
  confirmedBy: UserApp | null;
  /** How many collections the slip bundles. */
  paymentCount: number;
  /** The orders whose cash the slip carries, to count against before confirming. */
  invoices: RemittedInvoice[];
}

/** One order in a slip — mirrors CashRemittanceResponse.RemittedInvoice. */
export interface RemittedInvoice {
  invoiceId: number;
  invoiceNumber: string;
  clientName: string;
  amount: number;
}
