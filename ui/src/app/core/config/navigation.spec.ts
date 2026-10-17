import { RoleApp } from '../models';
import { navigationFor, openSections } from './navigation';

describe('sidebar sections', () => {
  const cashierAndStock = navigationFor([RoleApp.CASHIER, RoleApp.DEPOT_MANAGER]);

  it('keeps the whole menu open for an account with one role', () => {
    const cashier = navigationFor([RoleApp.CASHIER]);
    expect([...openSections(cashier, null, new Set())]).toEqual(['caisse']);
  });

  it('opens only the section of the page on screen for an account with several roles', () => {
    expect([...openSections(cashierAndStock, 'gestion-depot', new Set())]).toEqual(['gestion-depot']);
  });

  it('keeps open the sections unfolded by hand, besides the current one', () => {
    const open = openSections(cashierAndStock, 'caisse', new Set(['gestion-depot']));
    expect(open.has('caisse')).toBeTrue();
    expect(open.has('gestion-depot')).toBeTrue();
  });

  it('opens the first section on a page that belongs to none, such as the profile', () => {
    expect([...openSections(cashierAndStock, null, new Set())]).toEqual([cashierAndStock[0].segment]);
  });

  it('offre au super-admin les corrections de stock, que le serveur lui permet', () => {
    const admin = navigationFor([RoleApp.SUPER_ADMIN]);
    expect(admin[0].items.map((item) => item.path)).toContain('/admin/corrections');
  });
});
