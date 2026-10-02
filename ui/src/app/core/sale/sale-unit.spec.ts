import { Product } from '../models';
import { formatQuantity, parseQuantity, roundQuantity, stockLabel, unitByBarcode, unitsOf } from './sale-unit';

/** Loose rice counted in kapoka, sold by the kilo (3.5 kapoka) and the 50 kg sack (175). */
const vrac: Product = {
  id: 1,
  name: 'Riz vrac',
  price: 900,
  stockQuantity: 1743.5,
  unit: 'kapoka',
  barcode: null,
  category: null,
  lowStock: false,
  stockValue: 0,
  packagings: [
    { id: 5, label: 'kg', factor: 3.5, price: 3000, barcode: null },
    { id: 6, label: 'sac 50 kg', factor: 175, price: 145000, barcode: 'SAC' },
  ],
};

describe('sale units', () => {
  it('lists the base unit first, then the packagings', () => {
    expect(unitsOf(vrac).map((unit) => unit.label)).toEqual(['kapoka', 'kg', 'sac 50 kg']);
    expect(unitsOf(vrac)[0]).toEqual({ packagingId: null, label: 'kapoka', factor: 1, price: 900 });
  });

  it('finds the packaging a code was scanned from, and only a packaging', () => {
    expect(unitByBarcode(vrac, 'SAC')?.label).toBe('sac 50 kg');
    expect(unitByBarcode(vrac, 'other')).toBeNull();
  });

  it('says a stock in the largest unit it holds at least one of', () => {
    expect(stockLabel(vrac)).toBe('9,96 sac 50 kg');
    expect(stockLabel(vrac, 30)).toBe('8,57 kg');
    expect(stockLabel(vrac, 2)).toBe('2 kapoka');
    expect(stockLabel({ ...vrac, packagings: [] }, 12)).toBe('12 kapoka');
    expect(stockLabel({ ...vrac, unit: null, packagings: [] }, 12)).toBe('12');
  });

  it('reads a typed quantity, and refuses what is not one', () => {
    expect(parseQuantity('0,5')).toBe(0.5);
    expect(parseQuantity(' 2 ')).toBe(2);
    expect(parseQuantity('1.250')).toBe(1.25);
    expect(parseQuantity('0')).toBeNull();
    expect(parseQuantity('-1')).toBeNull();
    expect(parseQuantity('1,2345')).toBeNull();
    expect(parseQuantity('deux')).toBeNull();
  });

  it('keeps floating point out of the quantities', () => {
    expect(roundQuantity(0.1 * 3.5)).toBe(0.35);
    expect(formatQuantity(1.75)).toBe('1,75');
  });
});
