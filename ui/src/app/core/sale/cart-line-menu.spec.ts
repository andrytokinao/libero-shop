import { Product } from '../models';
import { CartLine } from './cart';
import { cartLineMenu, unitOfEntry } from './cart-line-menu';
import { unitsOf } from './sale-unit';

describe('cart-line-menu', () => {
  const rice = {
    id: 1,
    name: 'Riz',
    price: 900,
    unit: 'kapoka',
    stockQuantity: 500,
    packagings: [{ id: 7, label: 'sac', factor: 50, price: 42000 }],
  } as unknown as Product;
  const inKapoka: CartLine = { product: rice, unit: unitsOf(rice)[0], quantity: 2 };

  it('propose la quantité, les autres unités, puis le retrait en rouge', () => {
    const menu = cartLineMenu(inKapoka);

    expect(menu.map((entry) => entry.label)).toEqual(['Changer la quantité', 'Vendre en sac', 'Retirer du panier']);
    expect(menu[2].danger).toBeTrue();
  });

  it('l unité de la ligne n est pas reproposée', () => {
    const inSack: CartLine = { ...inKapoka, unit: unitsOf(rice)[1] };

    expect(cartLineMenu(inSack).map((entry) => entry.label)).toContain('Vendre en kapoka');
    expect(cartLineMenu(inSack).map((entry) => entry.label)).not.toContain('Vendre en sac');
  });

  it('retrouve l unité choisie', () => {
    const entry = cartLineMenu(inKapoka)[1];

    expect(unitOfEntry(inKapoka, entry.id)?.packagingId).toBe(7);
    expect(unitOfEntry(inKapoka, 'remove')).toBeNull();
  });
});
