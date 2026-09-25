import { DeliveryStatus, Invoice, PaymentStatus } from '../../core/models';

/** Label of the hand-over button: an unpaid order is settled on the spot. */
export function deliveryActionLabel(invoice: Invoice): string | null {
  if (invoice.deliveryStatus === DeliveryStatus.DELIVERED) {
    return null;
  }
  return invoice.paymentStatus === PaymentStatus.UNPAID
    ? 'Encaisser et remettre'
    : 'Remettre au client';
}

/** Feedback shown once the order left the depot. */
export function describeDelivery(result: { invoice: Invoice; collected: number }): string {
  const { invoice, collected } = result;
  if (collected > 0) {
    return (
      `${invoice.invoiceNumber} remise. ${collected.toLocaleString('fr-FR')} Ar ` +
      `encaissés en espèces — à verser à la caisse.`
    );
  }
  return `Commande ${invoice.invoiceNumber} remise à ${invoice.clientName}.`;
}
