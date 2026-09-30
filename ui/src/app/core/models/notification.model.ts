import { PaymentStatus } from './enums';

/** Mirrors com.houssen.liberoshop.notification.NotificationType. */
export enum NotificationType {
  /** A sale was recorded at the counter: goods are waiting to leave the depot. */
  SALE_CREATED = 'SALE_CREATED',
  /** An order was handed over at the depot: its seller learns the customer has the goods. */
  ORDER_DELIVERED = 'ORDER_DELIVERED',
  /** A storekeeper brought cash to the desk: a cashier has to count it and confirm. */
  REMITTANCE_SUBMITTED = 'REMITTANCE_SUBMITTED',
  /** A cashier confirmed receiving a storekeeper's cash: the orders are paid. */
  REMITTANCE_CONFIRMED = 'REMITTANCE_CONFIRMED',
}

/**
 * Mirrors the server's Notification envelope: the same shape whatever the kind, so the client
 * decodes one thing and dispatches on `type`.
 *
 * @typeParam T the shape of `data`, fixed per type — see the `…Data` interfaces below
 */
export interface AppNotification<T = unknown> {
  /** Unique per notification, so one received twice can be recognised. */
  id: string;
  type: NotificationType;
  /** Ready to show, in French. */
  title: string;
  message: string;
  /** ISO-8601 instant; the browser formats it in its own zone. */
  createdAt: string;
  data: T;
}

/** `data` of a SALE_CREATED notification — mirrors SaleNotifier.SaleCreated. */
export interface SaleCreatedData {
  invoiceId: number;
  invoiceNumber: string;
  clientName: string;
  totalAmount: number;
  /** UNPAID means cash is to be collected on hand-over. */
  paymentStatus: PaymentStatus;
  units: number;
  sellerName: string;
}

/** `data` of an ORDER_DELIVERED notification — mirrors DeliveryNotifier.OrderDelivered. */
export interface OrderDeliveredData {
  invoiceId: number;
  invoiceNumber: string;
  clientName: string;
  agentName: string;
  /** Cash taken at hand-over; 0 when the order was paid at the desk. */
  collected: number;
}

/** `data` of the REMITTANCE_* notifications — mirrors RemittanceNotifier.RemittanceData. */
export interface RemittanceData {
  remittanceId: number;
  amount: number;
  invoiceNumbers: string[];
  agentName: string;
  /** Null until a cashier has confirmed. */
  cashierName: string | null;
}
