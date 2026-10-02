import { DeliveryStatus, Invoice, PaymentStatus } from '../models';
import { isHandledBy } from './invoice-actions';

/**
 * The one button a row of the depot's queue carries: take the order on while it waits, serve it
 * once it is yours. An order someone else has taken shows no button — its handler chip says who
 * has it — and the API would refuse it anyway, this only spares the attempt.
 */
export type DeliveryRowAction = 'take' | 'handOver';

export function deliveryRowAction(invoice: Invoice, userId: number | null): DeliveryRowAction | null {
  if (invoice.deliveryStatus === DeliveryStatus.PENDING) {
    return 'take';
  }
  return isHandledBy(invoice, userId) ? 'handOver' : null;
}

/**
 * The row button's label, in the shop's own words (the vocabulary's `handOverAction`: "Remettre
 * au client", "Servir").
 */
export function deliveryActionLabel(
  handOverLabel: string,
  userId: number | null,
): (invoice: Invoice) => string | null {
  return (invoice) => {
    switch (deliveryRowAction(invoice, userId)) {
      case 'take':
        return "Je m'en occupe";
      case 'handOver':
        return handOverLabel;
      default:
        return null;
    }
  };
}

/** True when handing this order over first asks whether the customer pays now or at the till. */
export function asksHowToPay(invoice: Invoice, payAtDepot: boolean): boolean {
  return payAtDepot && invoice.paymentStatus === PaymentStatus.UNPAID;
}
