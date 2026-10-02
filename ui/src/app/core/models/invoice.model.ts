import { CancelReason, DeliveryStatus, PaymentStatus } from './enums';
import { Sale } from './sale.model';

/**
 * Mirrors com.houssen.liberoshop.entity.Invoice (table invoice), as served by
 * InvoiceResponse.
 */
export interface Invoice {
  id: number;
  /** Unique business identifier, e.g. F-1031. */
  invoiceNumber: string;
  /** LocalDateTime serialized as ISO-8601. */
  invoiceDate: string;
  clientName: string;
  paymentStatus: PaymentStatus;
  deliveryStatus: DeliveryStatus;
  /** Stays false until real printing is implemented. */
  printed: boolean;
  /** Total units on the invoice, summed server-side. */
  itemCount: number;
  sale: Sale;
  /** Who cancelled the order, when and why; null for an order that stands. */
  cancellation: InvoiceCancellation | null;
  /**
   * Where the money is while it is not in the till: in someone's hand (COLLECTED), or on a slip
   * awaiting the cashier (REMITTED). Null otherwise. Absent from the responses that only
   * confirm an action — the lists and the detail always carry it.
   */
  cashTrail?: CashTrail | null;
}

/** Mirrors InvoiceResponse.CashTrail. */
export interface CashTrail {
  /** Who took the money from the customer — and handed the slip in, once remitted. */
  holderId: number;
  holderName: string;
  /** The slip awaiting the cashier's count; null while the cash is still in hand. */
  remittanceId: number | null;
  /** The holder's photo version, to show their face beside the money. */
  holderPhotoVersion?: number | null;
}

export interface InvoiceCancellation {
  at: string;
  byName: string | null;
  reason: CancelReason;
  comment: string | null;
}

/** POST /api/invoices/{id}/cancel. */
export interface CancelInvoiceRequest {
  reason: CancelReason;
  /** Required for OTHER, and for an order already handed over. */
  comment: string | null;
}

/**
 * Compact stand-in returned where a full invoice would be a back-reference — on a
 * payment or a stock output. Matches InvoiceRefResponse.
 */
export interface InvoiceRef {
  id: number;
  invoiceNumber: string;
  clientName: string;
  totalAmount: number;
  deliveryStatus: DeliveryStatus;
}
