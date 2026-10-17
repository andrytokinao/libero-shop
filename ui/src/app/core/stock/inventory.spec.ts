import { Product } from '../models';
import { readCount, summarize } from './inventory';

describe('inventory', () => {
  const product = (id: number, stockQuantity: number, price = 1000) =>
    ({ id, name: `P${id}`, stockQuantity, price }) as Product;
  const eau = product(1, 10, 1200);
  const jus = product(2, 6);
  const sel = product(3, 4);

  it('un champ vide n est pas compté ; zéro l est', () => {
    expect(readCount('')).toBeNull();
    expect(readCount('0')).toBe(0);
    expect(readCount('2,5')).toBe(2.5);
    expect(readCount('1,2345')).toBeNull();
  });

  it('résume : écarts chiffrés, produits conformes, produits laissés vides', () => {
    const summary = summarize([eau, jus, sel], new Map([[1, '8'], [2, '6']]));

    expect(summary.counted).toBe(2);
    expect(summary.skipped).toBe(1);
    expect(summary.gaps.length).toBe(1);
    expect(summary.gaps[0].difference).toBe(-2);
    expect(summary.gaps[0].value).toBe(-2400);
  });

  it('signale les comptes illisibles plutôt que de les ignorer', () => {
    const summary = summarize([eau, jus], new Map([[1, 'douze'], [2, '7']]));

    expect(summary.unreadable).toEqual([eau]);
    expect(summary.gaps.map((gap) => gap.difference)).toEqual([1]);
  });
});
