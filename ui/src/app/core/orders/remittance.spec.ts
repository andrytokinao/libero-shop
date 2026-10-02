import { CashRemittance, RemittanceStatus, RoleApp, ShopFeatures } from '../models';
import { confirmsOwnSlip, describeSlip, remitLabel } from './remittance';

describe('remittance rules', () => {
  const features = (dualControlRemittance: boolean) => ({ dualControlRemittance }) as ShopFeatures;

  it('lets a cashier put the cash straight in the till when nobody else has to count it', () => {
    expect(confirmsOwnSlip([RoleApp.CASHIER, RoleApp.DEPOT_AGENT], features(false))).toBeTrue();
    expect(remitLabel(true)).toBe('Mettre en caisse');
  });

  it('keeps the second person when the shop asks for one, and for whoever holds no till', () => {
    expect(confirmsOwnSlip([RoleApp.CASHIER, RoleApp.DEPOT_AGENT], features(true))).toBeFalse();
    expect(confirmsOwnSlip([RoleApp.DEPOT_AGENT], features(false))).toBeFalse();
    expect(remitLabel(false)).toBe('Remettre à la caisse');
  });

  it('tells what became of the cash from the slip itself', () => {
    const slip = { id: 12, amount: 7400, status: RemittanceStatus.CONFIRMED } as CashRemittance;

    expect(describeSlip(slip)).toContain('mis en caisse');
    expect(describeSlip({ ...slip, status: RemittanceStatus.PENDING })).toContain('en attente de confirmation');
  });
});
