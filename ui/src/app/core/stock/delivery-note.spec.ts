import { Product, ProductCost } from '../models';
import { addToNote, deliveryRequest, noteTotal } from './delivery-note';

describe('delivery note', () => {
  const milk: Product = {
    id: 3,
    name: 'Lait en poudre 400g',
    price: 9500,
    stockQuantity: 14,
    unit: null,
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: 0,
    packagings: [{ id: 30, label: 'carton', factor: 12, price: 110000, barcode: null, reference: null, referenceQuantity: 12 }],
  };
  const milkCost = {
    productId: 3,
    units: [
      { packagingId: null, lastCost: 7100 },
      { packagingId: 30, lastCost: 84000 },
    ],
  } as unknown as ProductCost;

  it('starts a line at the last price paid for that unit', () => {
    const lines = addToNote([], milk, 30, milkCost);
    expect(lines).toEqual([{ product: milk, packagingId: 30, quantity: '1', unitCost: '84000' }]);
  });

  it('counts a second scan of the same carton as one more carton', () => {
    const twice = addToNote(addToNote([], milk, 30, milkCost), milk, 30, milkCost);
    expect(twice.length).toBe(1);
    expect(twice[0].quantity).toBe('2');
  });

  it('keeps the unit and the carton apart', () => {
    const lines = addToNote(addToNote([], milk, 30, milkCost), milk, null, milkCost);
    expect(lines.map((line) => line.packagingId)).toEqual([30, null]);
  });

  it('totals the priced lines and counts the others', () => {
    const lines = [
      { product: milk, packagingId: 30, quantity: '2', unitCost: '84000' },
      { product: milk, packagingId: null, quantity: '5', unitCost: '' },
    ];
    expect(noteTotal(lines)).toEqual({ amount: 168000, unpriced: 1 });
  });

  it('sends an empty price as unknown, and refuses a missing quantity', () => {
    const lines = [{ product: milk, packagingId: null, quantity: '1,5', unitCost: '' }];
    expect(deliveryRequest(7, lines)).toEqual({
      supplierId: 7,
      lines: [{ productId: 3, packagingId: null, quantity: 1.5, unitCost: null }],
    });
    expect(deliveryRequest(7, [{ ...lines[0], quantity: '' }])).toBeNull();
    expect(deliveryRequest(7, [])).toBeNull();
  });
});
