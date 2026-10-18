import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Product, UserApp } from '../models';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { Cart } from './cart';
import { keepCartDraft } from './cart-draft';
import { HELD_SALES_STORAGE } from './held-sales';

function product(id: number): Product {
  return {
    id,
    name: `Produit ${id}`,
    price: 1000,
    stockQuantity: 10,
    unit: null,
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: 10000,
  };
}

describe('keepCartDraft', () => {
  let storage: Map<string, string>;
  let user: ReturnType<typeof signal<UserApp | null>>;

  beforeEach(() => {
    storage = new Map();
    user = signal({ username: 'fatima' } as UserApp);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: HELD_SALES_STORAGE,
          useValue: {
            getItem: (key: string) => storage.get(key) ?? null,
            setItem: (key: string, value: string) => storage.set(key, value),
            removeItem: (key: string) => storage.delete(key),
          },
        },
        { provide: AuthService, useValue: { currentUser: user } },
      ],
    });
  });

  /** A screen opening: its cart and client field, kept from now on. */
  function openScreen(): { cart: Cart; client: ReturnType<typeof signal<string>> } {
    const cart = new Cart();
    const client = signal('');
    TestBed.runInInjectionContext(() => keepCartDraft('caisse', cart, client));
    TestBed.flushEffects();
    return { cart, client };
  }

  it('après un rechargement, le panier et le nom du client reviennent', () => {
    const first = openScreen();
    first.cart.add(product(1), 3);
    first.client.set('Rina');
    TestBed.flushEffects();

    const reloaded = openScreen();

    expect(reloaded.cart.lines().map((line) => [line.product.id, line.quantity])).toEqual([[1, 3]]);
    expect(reloaded.client()).toBe('Rina');
    expect(TestBed.inject(ToastService).message()).toContain('Panier en cours retrouvé');
  });

  it('une vente terminée ne laisse rien derrière elle', () => {
    const first = openScreen();
    first.cart.add(product(1), 1);
    TestBed.flushEffects();
    first.cart.clear();
    TestBed.flushEffects();

    expect(openScreen().cart.isEmpty()).toBeTrue();
    expect(storage.size).toBe(0);
  });

  it('un collègue qui se connecte sur la même caisse ne trouve pas le panier d un autre', () => {
    const first = openScreen();
    first.cart.add(product(1), 2);
    TestBed.flushEffects();

    user.set({ username: 'hary' } as UserApp);

    expect(openScreen().cart.isEmpty()).toBeTrue();
  });
});
