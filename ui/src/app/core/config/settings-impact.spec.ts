import { BusinessType, DEFAULT_SHOP_SETTINGS, RoleApp, ShopSettings } from '../models';
import { menuChanges, switchEffect } from './settings-impact';

describe('settings-impact', () => {
  const depot: ShopSettings = DEFAULT_SHOP_SETTINGS;

  it('ne liste rien quand la configuration ne change pas', () => {
    expect(menuChanges(depot, { ...depot })).toEqual([]);
  });

  it('retire tout le module Dépôt quand la remise n est plus séparée', () => {
    const changes = menuChanges(depot, { ...depot, separateDelivery: false, payAtDepot: false, dualControlRemittance: false });

    const module = changes.find((change) => change.role === RoleApp.DEPOT_AGENT)!;
    expect(module.removed).toContain('Caisse dépôt');
    expect(module.removed).toContain('Remise de commande');
    expect(module.added).toEqual([]);
    // No one brings money to the till any more: the cashier's inbox goes too.
    expect(changes.find((change) => change.role === RoleApp.CASHIER)!.removed).toEqual(['Versements reçus']);
  });

  it('dit les écrans renommés par le type d activité', () => {
    const changes = menuChanges(depot, { ...depot, businessType: BusinessType.RESTAURANT });

    const module = changes.find((change) => change.role === RoleApp.DEPOT_AGENT)!;
    expect(module.module).toBe('Cuisine');
    expect(module.renamed).toContain({ from: 'Remise de commande', to: 'Commandes à servir' });
  });

  it('donne ce qu un interrupteur ajoute au menu', () => {
    expect(switchEffect(depot, 'onlineOrdering')).toBe('« Tables & QR codes » (Admin)');
    expect(switchEffect(depot, 'orderTakerCollects')).toBe('« Argent à remettre » (Commandes)');
  });

  it('ne dit rien d un interrupteur qui ne change aucun menu', () => {
    expect(switchEffect(depot, 'cancelAfterDelivery')).toBe('');
    expect(switchEffect(depot, 'dualControlRemittance')).toBe('');
  });
});
