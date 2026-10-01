import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Product, UserApp } from '../models';
import { AuthService } from '../services/auth.service';
import { Cart } from './cart';
import { HELD_SALES_STORAGE, HeldSales } from './held-sales';

/** A Storage held in a Map, so each test starts from nothing and can look inside. */
class MemoryStorage implements Storage {
  private readonly items = new Map<string, string>();
  get length(): number {
    return this.items.size;
  }
  clear(): void {
    this.items.clear();
  }
  getItem(key: string): string | null {
    return this.items.get(key) ?? null;
  }
  key(index: number): string | null {
    return [...this.items.keys()][index] ?? null;
  }
  removeItem(key: string): void {
    this.items.delete(key);
  }
  setItem(key: string, value: string): void {
    this.items.set(key, value);
  }
}

function product(id: number, price = 1000): Product {
  return {
    id,
    name: `Produit ${id}`,
    price,
    stockQuantity: 10,
    unit: null,
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: price * 10,
  };
}

describe('HeldSales', () => {
  let storage: MemoryStorage;
  let user: ReturnType<typeof signal<UserApp | null>>;
  let held: HeldSales;

  beforeEach(() => {
    storage = new MemoryStorage();
    user = signal({ username: 'fatima' } as UserApp);
    TestBed.configureTestingModule({
      providers: [
        { provide: HELD_SALES_STORAGE, useValue: storage },
        { provide: AuthService, useValue: { currentUser: user } },
      ],
    });
    held = TestBed.inject(HeldSales);
  });

  function cartWith(...lines: [Product, number][]): Cart {
    const cart = new Cart();
    lines.forEach(([p, quantity]) => cart.add(p, quantity));
    return cart;
  }

  it('sets a basket aside with its client, and empties the till', () => {
    const cart = cartWith([product(1), 2], [product(2), 1]);

    const sale = held.hold(cart, '  Rakoto ');

    expect(cart.isEmpty()).toBeTrue();
    expect(sale?.clientName).toBe('Rakoto');
    expect(held.list().map((s) => s.lines.map((l) => [l.product.id, l.quantity]))).toEqual([
      [
        [1, 2],
        [2, 1],
      ],
    ]);
  });

  it('holds nothing for an empty cart', () => {
    expect(held.hold(new Cart(), 'Rakoto')).toBeNull();
    expect(held.count()).toBe(0);
  });

  it('hands a held sale back once, oldest first in the list', () => {
    const first = held.hold(cartWith([product(1), 1]), 'Rakoto')!;
    held.hold(cartWith([product(2), 1]), 'Soa');

    expect(held.list().map((s) => s.clientName)).toEqual(['Rakoto', 'Soa']);
    expect(held.take(first.id)?.clientName).toBe('Rakoto');
    expect(held.take(first.id)).toBeNull();
    expect(held.list().map((s) => s.clientName)).toEqual(['Soa']);
  });

  it('survives a reload: what is written is read back by a fresh instance', () => {
    held.hold(cartWith([product(1), 3]), 'Rakoto');

    const afterReload = TestBed.runInInjectionContext(() => new HeldSales());

    expect(afterReload.list()[0].lines[0].quantity).toBe(3);
  });

  it('keeps each cashier\'s customers to themselves', () => {
    held.hold(cartWith([product(1), 1]), 'Rakoto');

    user.set({ username: 'hary' } as UserApp);
    expect(held.count()).toBe(0);

    user.set({ username: 'fatima' } as UserApp);
    expect(held.count()).toBe(1);
  });

  it('shrugs off a corrupted entry rather than breaking the till', () => {
    storage.setItem('liberoshop.held-sales.fatima', '{not json');

    expect(held.list()).toEqual([]);
  });
});
