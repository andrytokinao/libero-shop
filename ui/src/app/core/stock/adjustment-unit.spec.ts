import { Product } from '../models';
import { SaleUnit } from '../sale/sale-unit';
import { conversionLine, inBaseUnits } from './adjustment-unit';

describe('adjustment-unit', () => {
  const juice = { unit: 'bouteille' } as Product;
  const bottle: SaleUnit = { packagingId: null, label: 'bouteille', factor: 1, price: 3000 };
  const carton: SaleUnit = { packagingId: 7, label: 'carton', factor: 12, price: 34000 };

  it('2 cartons cassés font 24 bouteilles', () => {
    expect(inBaseUnits(2, carton)).toBe(24);
    expect(inBaseUnits(2, bottle)).toBe(2);
  });

  it('relit la conversion sous le champ, seulement pour un carton', () => {
    expect(conversionLine(2, carton, juice)).toBe('2 carton = 24 bouteille');
    expect(conversionLine(2, bottle, juice)).toBeNull();
    expect(conversionLine(null, carton, juice)).toBeNull();
  });

  it('un demi-carton reste exact au millième', () => {
    expect(inBaseUnits(0.5, { ...carton, factor: 0.333 })).toBe(0.167);
  });
});
