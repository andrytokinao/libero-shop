import { UnitModel } from '../models';
import { fold, planModel } from './unit-model-plan';

const WEIGHT: UnitModel = {
  id: 1,
  name: 'Poids',
  baseLabel: 'g',
  units: [
    { id: 11, label: 'kg', quantity: 1000, reference: null, baseFactor: 1000 },
    { id: 12, label: 'tonne', quantity: 1000, reference: 'kg', baseFactor: 1_000_000 },
  ],
};

function statuses(plan: ReturnType<typeof planModel>): string[] {
  return plan.units.map((u) => `${u.label}:${u.status}`);
}

describe('planModel', () => {
  it('places the model straight on a product counted in kg', () => {
    const plan = planModel(WEIGHT, { baseUnit: 'kg', basePrice: 1200, packagings: [] }, null);

    expect(plan.needsAnchor).toBeFalse();
    expect(statuses(plan)).toEqual(['g:ok', 'kg:base', 'tonne:ok']);
    expect(plan.units[0].factor).toBe(0.001);
    expect(plan.units[2].factor).toBe(1000);
    expect(plan.units[2].suggestedPrice).toBe(1_200_000);
  });

  it('asks for an anchor when the product shares no unit with the model', () => {
    const plan = planModel(WEIGHT, { baseUnit: 'kapoka', basePrice: 900, packagings: [] }, null);

    expect(plan.needsAnchor).toBeTrue();
    expect(plan.units.every((u) => u.status === 'no-anchor')).toBeTrue();
  });

  it('works from "1 kg = 3,5 kapoka", and greys out the gram it cannot count', () => {
    const plan = planModel(
      WEIGHT,
      { baseUnit: 'kapoka', basePrice: 900, packagings: [] },
      { label: 'kg', quantity: 3.5 },
    );

    expect(statuses(plan)).toEqual(['g:too-precise', 'kg:ok', 'tonne:ok']);
    expect(plan.units[1].factor).toBe(3.5);
    expect(plan.units[2].factor).toBe(3500);
  });

  it('reads the anchor off a kg the product already has', () => {
    const plan = planModel(
      WEIGHT,
      { baseUnit: 'kapoka', basePrice: 900, packagings: [{ label: 'Kg', factor: 3.5 }] },
      null,
    );

    expect(statuses(plan)).toEqual(['g:too-precise', 'kg:present', 'tonne:ok']);
    expect(plan.units[2].factor).toBe(3500);
  });

  it('refuses a unit worth exactly one base unit', () => {
    const plan = planModel(
      WEIGHT,
      { baseUnit: 'kilo', basePrice: 900, packagings: [] },
      { label: 'kg', quantity: 1 },
    );

    expect(plan.units[1].status).toBe('same-as-base');
  });

  it('folds names like the server', () => {
    expect(fold(' Sac 50 kg ')).toBe(fold('sac50KG'));
    expect(fold('Pièce')).toBe('piece');
  });
});
