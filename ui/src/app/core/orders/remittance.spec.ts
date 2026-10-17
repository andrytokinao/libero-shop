import { CashRemittance, RemittanceStatus, RoleApp, ShopFeatures } from '../models';
import { confirmsOwnSlip, describeSlip, remitLabel, remitQuestion } from './remittance';

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

  it('asks before handing cash over, with the amount and the order it is for', () => {
    const question = remitQuestion(['F-1007'], 14000, false);

    expect(question.message).toContain('la facture F-1007');
    expect(question.message).toContain('un caissier comptera');
    expect(question.confirmLabel).toBe(`Remettre ${(14000).toLocaleString('fr-FR')} Ar`);
  });

  it('names every order of "Tout remettre", and says the cash goes in the till for a cashier alone', () => {
    const question = remitQuestion(['F-1004', 'F-1006'], 30000, true);

    expect(question.message).toContain('2 factures (F-1004, F-1006)');
    expect(question.title).toBe('Mettre en caisse ?');
    expect(question.confirmLabel).toContain('en caisse');
  });
});
