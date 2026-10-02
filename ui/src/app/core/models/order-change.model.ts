import { CashRemittance } from './cash-remittance.model';
import { Invoice } from './invoice.model';

/** Mirrors the constants of OrderChangePublisher.Change: what happened to the orders. */
export enum OrderChangeKind {
  /** Recorded at the desk: waiting at the depot. */
  ADDED = 'ADDED',
  /** Handed over to the customer. */
  DELIVERED = 'DELIVERED',
  /** Settled at the till, or its cash taken by an order taker. */
  PAID = 'PAID',
  /** Cancelled while unpaid. */
  CANCELLED = 'CANCELLED',
  /** Sent to the printer. */
  PRINTED = 'PRINTED',
  /** Its cash brought to the desk, not confirmed yet. */
  CASH_REMITTED = 'CASH_REMITTED',
  /** Its cash confirmed in the till: the order is paid. */
  CASH_CONFIRMED = 'CASH_CONFIRMED',
}

/** What the server publishes on `/topic/orders` — mirrors OrderChangePublisher.Change. */
export interface OrderChange {
  change: OrderChangeKind;
  invoiceNumbers: string[];
  /** The orders as they now stand; empty when the server could not read them back. */
  invoices: Invoice[];
  /** The slip, for the CASH_* changes; null otherwise. */
  remittance: CashRemittance | null;
}
