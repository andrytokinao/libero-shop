import { Product } from '../models';
import { findByCode, looksLikeCode, matchProducts, searchKey } from './product-pick';

function product(id: number, name: string, barcode: string | null = null, cartonCode: string | null = null): Product {
  return {
    id,
    name,
    price: 1000,
    stockQuantity: 10,
    unit: null,
    barcode,
    category: null,
    lowStock: false,
    stockValue: 10000,
    packagings: cartonCode
      ? [{ id: id * 10, label: 'carton', factor: 12, price: 11000, barcode: cartonCode, reference: null, referenceQuantity: 12 }]
      : [],
  };
}

describe('product-pick', () => {
  const riz = product(1, 'Riz 5kg', '6001001000015');
  const cafe = product(2, 'Café moulu 250g');
  const lait = product(3, 'Lait en poudre 400g', '6001', '6009009000099');
  const huileRiz = product(4, 'Huile de riz 1L');
  const catalogue = [riz, cafe, lait, huileRiz];

  it('reads a name without its accents or its capitals', () => {
    expect(searchKey('  Café   Moulu ')).toBe('cafe moulu');
  });

  it('waits for two letters before proposing anything', () => {
    expect(matchProducts(catalogue, 'r')).toEqual([]);
    expect(matchProducts(catalogue, ' ')).toEqual([]);
  });

  it('finds a name from every word typed, in any order, accents aside', () => {
    expect(matchProducts(catalogue, 'cafe')).toEqual([cafe]);
    expect(matchProducts(catalogue, '5kg riz')).toEqual([riz]);
  });

  it('puts the names that start with the word first', () => {
    expect(matchProducts(catalogue, 'riz')).toEqual([riz, huileRiz]);
  });

  it('finds the product of a code printed on its base unit', () => {
    expect(findByCode(catalogue, '6001001000015')).toEqual({ product: riz, packagingId: null });
  });

  it('finds the carton a code is printed on, so the receipt can be counted in cartons', () => {
    expect(findByCode(catalogue, ' 6009009000099 ')).toEqual({ product: lait, packagingId: 30 });
  });

  it('takes only a whole code, never the start of one', () => {
    expect(findByCode(catalogue, '600100')).toBeNull();
    expect(findByCode(catalogue, '')).toBeNull();
  });

  it('reads six digits or more as a code, anything shorter or with letters as words', () => {
    expect(looksLikeCode(' 6001001000015 ')).toBeTrue();
    expect(looksLikeCode('12')).toBeFalse();
    expect(looksLikeCode('riz 5kg')).toBeFalse();
  });
});
