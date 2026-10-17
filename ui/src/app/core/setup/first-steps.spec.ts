import { FirstSteps } from '../models';
import { firstStepsOf, readiness } from './first-steps';

describe('first-steps', () => {
  const none: FirstSteps = {
    product: false,
    purchasePrice: false,
    barcode: false,
    stock: false,
    cashier: false,
    sale: false,
  };

  it('une boutique neuve est prête à 0 %, et à 50 % avec trois étapes sur six', () => {
    expect(readiness(firstStepsOf(none, 'admin'))).toBe(0);
    expect(readiness(firstStepsOf({ ...none, product: true, purchasePrice: true, stock: true }, 'admin'))).toBe(50);
  });

  it('le premier produit se crée sur place', () => {
    const product = firstStepsOf(none, 'manager')[0];
    expect(product.createsProduct).toBeTrue();
    expect(product.link).toBeUndefined();
  });

  it('chaque rôle est envoyé vers ses propres pages', () => {
    const admin = firstStepsOf(none, 'admin');
    const manager = firstStepsOf(none, 'manager');

    expect(admin.find((step) => step.key === 'stock')!.link).toBe('/admin/stock-global');
    expect(manager.find((step) => step.key === 'stock')!.link).toBe('/gestion-depot/approvisionnement');
    expect(manager.find((step) => step.key === 'purchasePrice')!.link).toBe('/gestion-depot/prix-et-unites');
  });

  it('le responsable de dépôt ne crée pas de compte : l étape le dit sans lien', () => {
    const cashier = firstStepsOf(none, 'manager').find((step) => step.key === 'cashier')!;
    expect(cashier.link).toBeUndefined();
    expect(cashier.note).toBe("par l'administrateur");
    expect(firstStepsOf(none, 'admin').find((step) => step.key === 'cashier')!.link).toBe('/admin/utilisateurs');
  });
});
