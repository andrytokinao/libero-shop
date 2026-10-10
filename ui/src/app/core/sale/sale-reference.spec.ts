import { PaymentMethod, PaymentStatus } from '../models';
import { SaleDraft, SaleReference } from './sale-reference';

describe('SaleReference', () => {
  const basket: SaleDraft = {
    clientName: 'Rasoa',
    paymentStatus: PaymentStatus.PAID,
    paymentMethod: PaymentMethod.CASH,
    lines: [{ productId: 7, packagingId: null, quantity: 2 }],
  };

  it('keeps the same reference for every attempt to post the same basket', () => {
    const reference = new SaleReference();

    const first = reference.for(basket);
    // Built again by the screen, as it is on each press of "Encaisser".
    const retry = reference.for({ ...basket, lines: [{ ...basket.lines[0] }] });

    expect(retry).toBe(first);
    expect(first).toMatch(/^[0-9a-f]{32}$/);
  });

  it('draws another once the basket changed, so an edited sale is never mistaken for a retry', () => {
    const reference = new SaleReference();
    const first = reference.for(basket);

    expect(reference.for({ ...basket, lines: [{ productId: 7, packagingId: null, quantity: 3 }] })).not.toBe(first);
    expect(reference.for({ ...basket, clientName: 'Rakoto' })).not.toBe(first);
    expect(reference.for({ ...basket, paymentStatus: PaymentStatus.UNPAID })).not.toBe(first);
  });

  it('draws another after a sale went through, even for the very same goods', () => {
    const reference = new SaleReference();
    const first = reference.for(basket);

    reference.renew();

    expect(reference.for(basket)).not.toBe(first);
  });

  it('gives two tills different references for the same basket', () => {
    expect(new SaleReference().for(basket)).not.toBe(new SaleReference().for(basket));
  });
});
