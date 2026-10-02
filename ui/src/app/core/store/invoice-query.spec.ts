import { DeliveryStatus, PaymentStatus } from '../models';
import { anInvoice } from './invoice.fixture';
import { matchesInvoiceQuery, newestInvoiceFirst } from './invoice-query';

const context = { userId: 7, today: '2026-10-02' };

describe('matchesInvoiceQuery', () => {
  it('takes every order when nothing is asked', () => {
    expect(matchesInvoiceQuery(anInvoice(1), {}, context)).toBeTrue();
  });

  it('keeps "mine" to the orders the person sold', () => {
    expect(matchesInvoiceQuery(anInvoice(1), { mine: true }, context)).toBeTrue();
    expect(matchesInvoiceQuery(anInvoice(2, {}, 8), { mine: true }, context)).toBeFalse();
  });

  it('filters on both statuses', () => {
    const delivered = anInvoice(1, { deliveryStatus: DeliveryStatus.DELIVERED });

    expect(matchesInvoiceQuery(delivered, { deliveryStatus: DeliveryStatus.PENDING }, context)).toBeFalse();
    expect(matchesInvoiceQuery(delivered, { deliveryStatus: DeliveryStatus.DELIVERED }, context)).toBeTrue();
    expect(matchesInvoiceQuery(delivered, { paymentStatus: PaymentStatus.PAID }, context)).toBeFalse();
  });

  it('keeps "today" to the orders of the current day', () => {
    const yesterday = anInvoice(1, { invoiceDate: '2026-10-01T23:59:59' });

    expect(matchesInvoiceQuery(yesterday, { todayOnly: true }, context)).toBeFalse();
    expect(matchesInvoiceQuery(anInvoice(2), { todayOnly: true }, context)).toBeTrue();
  });

  it('searches the number, the client and the seller, whatever the case', () => {
    const order = anInvoice(31);

    expect(matchesInvoiceQuery(order, { search: 'f-1031' }, context)).toBeTrue();
    expect(matchesInvoiceQuery(order, { search: ' RINA ' }, context)).toBeTrue();
    expect(matchesInvoiceQuery(order, { search: 'fatima' }, context)).toBeTrue();
    expect(matchesInvoiceQuery(order, { search: 'hery' }, context)).toBeFalse();
  });
});

describe('newestInvoiceFirst', () => {
  it('sorts like the server: latest date first, then highest id', () => {
    const rows = [
      anInvoice(1, { invoiceDate: '2026-10-02T08:00:00' }),
      anInvoice(2, { invoiceDate: '2026-10-02T10:00:00' }),
      anInvoice(3, { invoiceDate: '2026-10-02T08:00:00' }),
    ];

    expect(rows.sort(newestInvoiceFirst).map((row) => row.id)).toEqual([2, 3, 1]);
  });
});
