import { StockAdjustment, Supply } from '../models';
import { adjustmentActions, receiptHtml, supplyActions } from './movement-actions';

describe('movement actions', () => {
  const supply = {
    id: 5,
    quantity: 24,
    receivedQuantity: 2,
    unitLabel: 'carton',
    movementDate: '2026-10-17T09:00:00',
    product: { id: 3, name: 'Lait <400g>', unit: null },
    performedBy: { fullName: 'Nadia Rasolofo' },
    supplier: { id: 2, name: 'Distri Lait SARL' },
    withoutSupplier: false,
    unitCost: 85200,
    totalCost: 170400,
    cancelledAt: null,
  } as unknown as Supply;

  const broken = { id: 9, quantity: -3, reason: 'BREAKAGE', cancelledAt: null } as unknown as StockAdjustment;

  it('offers to cancel a receipt that stands, to whoever may', () => {
    expect(supplyActions(supply, true).map((a) => a.id)).toEqual(['history', 'print', 'cancel']);
    expect(supplyActions(supply, false).map((a) => a.id)).toEqual(['history', 'print']);
    expect(supplyActions({ ...supply, cancelledAt: '2026-10-17T10:00:00' }, true).map((a) => a.id)).toEqual([
      'history',
      'print',
    ]);
  });

  it('never offers to cancel a cancellation', () => {
    expect(adjustmentActions(broken).map((a) => a.id)).toEqual(['history', 'cancel']);
    expect(adjustmentActions({ ...broken, reason: 'CANCELLATION' }).map((a) => a.id)).toEqual(['history']);
  });

  it('prints the receipt with its figures, the product name escaped', () => {
    const html = receiptHtml(supply, 'Libero Shop');
    expect(html).toContain('Lait &lt;400g&gt;');
    expect(html).toContain('Distri Lait SARL');
    expect(html).toContain('carton');
  });
});
