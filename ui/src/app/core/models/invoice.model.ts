import { DeliveryStatus, PaymentStatus } from './enums';
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
