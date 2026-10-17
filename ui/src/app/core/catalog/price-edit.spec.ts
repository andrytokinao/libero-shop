import { marginAt, typedPrice } from './price-edit';

describe('price-edit', () => {
  it('lit un prix tapé avec espaces ou virgule', () => {
    expect(typedPrice('12 500')).toBe(12500);
    expect(typedPrice('1250,5')).toBe(1250.5);
  });

  it('refuse ce que le serveur refuserait : vide, zéro, trois décimales, lettres', () => {
    expect(typedPrice('')).toBeNull();
    expect(typedPrice('0')).toBeNull();
    expect(typedPrice('10,125')).toBeNull();
    expect(typedPrice('douze')).toBeNull();
  });

  it('recalcule le bénéfice au prix tapé, et le dit négatif sous le coût', () => {
    expect(marginAt(12500, 9400)).toEqual({ margin: 3100, rate: 24.8 });
    expect(marginAt(9000, 9400)!.margin).toBe(-400);
  });

  it('pas de bénéfice tant que le coût ou le prix manque', () => {
    expect(marginAt(null, 9400)).toBeNull();
    expect(marginAt(12500, null)).toBeNull();
  });
});
