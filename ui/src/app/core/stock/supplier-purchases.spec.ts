import { Supply } from '../models';
import { purchasesFrom } from './supplier-purchases';

function supply(id: number, product: string, date: string, cost: number | null, supplierId = 2, unitLabel: string | null = null): Supply {
  return {
    id,
    quantity: 1,
    receivedQuantity: 1,
    unitLabel,
    movementDate: date,
    product: { id: product.length, name: product, unit: null },
    supplier: { id: supplierId, name: 'Distri Lait' },
    unitCost: cost,
    cancelledAt: null,
  } as unknown as Supply;
}

describe('supplier purchases', () => {
  it('gives the last price paid for each product, and how many receipts', () => {
    const lines = purchasesFrom(
      [
        supply(1, 'Lait', '2026-10-13T09:00:00', 7100),
        supply(2, 'Lait', '2026-10-17T09:00:00', 7300),
        supply(3, 'Yaourt', '2026-10-17T09:00:00', null),
        supply(4, 'Riz', '2026-10-15T09:00:00', 9400, 9),
      ],
      2,
    );

    expect(lines.map((l) => [l.productName, l.lastCost, l.receipts])).toEqual([
      ['Lait', 7300, 2],
      ['Yaourt', null, 1],
    ]);
  });

  it('keeps the carton and the piece apart, and leaves cancelled receipts out', () => {
    const cancelled = { ...supply(3, 'Lait', '2026-10-18T09:00:00', 1), cancelledAt: '2026-10-18T10:00:00' } as Supply;
    const lines = purchasesFrom(
      [supply(1, 'Lait', '2026-10-13T09:00:00', 7100), supply(2, 'Lait', '2026-10-17T09:00:00', 85200, 2, 'carton'), cancelled],
      2,
    );

    expect(lines.map((l) => [l.unitLabel, l.lastCost])).toEqual([
      [null, 7100],
      ['carton', 85200],
    ]);
  });
});
