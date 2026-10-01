import { DeliveryStatus, Invoice, PaymentStatus } from '../models';

/**
 * Label of the hand-over button. An unpaid order, where the shop takes money on hand-over, then
 * asks how the customer pays (HandOverDialogComponent); otherwise the order is simply handed over.
 *
 * <p>Returning null hides the action for an order already delivered or cancelled — the API
 * refuses a second hand-over with a 409, this only spares the user the attempt.
 */
export function deliveryActionLabel(invoice: Invoice): string | null {
  if (invoice.deliveryStatus !== DeliveryStatus.PENDING) {
    return null;
  }
  return 'Remettre au client';
}

/** True when handing this order over first asks whether the customer pays now or at the till. */
export function asksHowToPay(invoice: Invoice, payAtDepot: boolean): boolean {
  return payAtDepot && invoice.paymentStatus === PaymentStatus.UNPAID;
}
