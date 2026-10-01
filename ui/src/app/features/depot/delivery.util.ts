import { DeliveryStatus, Invoice, PaymentStatus } from '../../core/models';

/**
 * Label of the hand-over button: an unpaid order is settled on the spot when the shop takes
 * money at hand-over, and left for the till otherwise.
 *
 * <p>Returning null hides the action for an order already delivered — the API refuses a
 * second hand-over with a 409, this only spares the user the attempt.
 */
export function deliveryActionLabel(payAtDepot: boolean): (invoice: Invoice) => string | null {
  return (invoice) => {
    if (invoice.deliveryStatus === DeliveryStatus.DELIVERED) {
      return null;
    }
    return payAtDepot && invoice.paymentStatus === PaymentStatus.UNPAID
      ? 'Encaisser et remettre'
      : 'Remettre au client';
  };
}
