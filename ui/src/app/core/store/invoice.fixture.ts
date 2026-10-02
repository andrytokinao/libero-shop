import { DeliveryStatus, Invoice, PaymentStatus } from '../models';

/** An order of the test shop, for the specs; only what the queries look at varies. */
export function anInvoice(id: number, changes: Partial<Invoice> = {}, sellerId = 7): Invoice {
  return {
    id,
    invoiceNumber: `F-${1000 + id}`,
    invoiceDate: '2026-10-02T09:30:00',
    clientName: 'Rina',
    paymentStatus: PaymentStatus.UNPAID,
    deliveryStatus: DeliveryStatus.PENDING,
    printed: false,
    itemCount: 2,
    sale: {
      id,
      saleDate: '2026-10-02T09:30:00',
      paymentStatus: PaymentStatus.UNPAID,
      totalAmount: 12000,
      seller: { id: sellerId, fullName: 'Fatima Caisse', username: 'fatima', roles: [], enabled: true },
      lines: [],
    },
    cancellation: null,
    cashTrail: null,
    ...changes,
  };
}
