import { DeliveryStatus, PaymentStatus } from './enums';
import { Sale } from './sale.model';

/** Mirrors com.houssen.libertyshop.entity.Invoice (table invoice). */
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
  sale: Sale;
}
