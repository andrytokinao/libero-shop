import { DeliveryStatus, Invoice, PaymentStatus } from '../../core/models';

/**
 * Label of the hand-over button: an unpaid order is settled on the spot.
 *
 * <p>Returning null hides the action for an order already delivered — the API refuses a
 * second hand-over with a 409, this only spares the user the attempt.
 */
export function deliveryActionLabel(invoice: Invoice): string | null {
  if (invoice.deliveryStatus === DeliveryStatus.DELIVERED) {
    return null;
  }
  return invoice.paymentStatus === PaymentStatus.UNPAID
    ? 'Encaisser et remettre'
    : 'Remettre au client';
}
