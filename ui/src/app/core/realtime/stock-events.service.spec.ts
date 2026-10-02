import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of } from 'rxjs';
import { CatalogApi } from '../api/catalog.api';
import { Product, StockChange } from '../models';
import { AuthService } from '../services/auth.service';
import { Cart } from '../sale/cart';
import { followStock } from '../sale/follow-stock';
import { ProductQuery, ProductStore } from '../store/product.store';
import { RealtimeService } from './realtime.service';
import { STOCK_TOPIC, StockEvents } from './stock-events.service';

function product(id: number, stockQuantity: number, name = `Produit ${id}`): Product {
  return {
    id,
    name,
    price: 1000,
    stockQuantity,
    unit: null,
    barcode: null,
    category: null,
    lowStock: stockQuantity < 5,
    stockValue: 1000 * stockQuantity,
  };
}

/**
 * The stock as every screen sees it, fed by a fake socket: a movement lands once in the store,
 * and the grids, stock tables and carts reading it follow.
 */
describe('StockEvents', () => {
  let topic: Subject<StockChange>;
  let signedIn: ReturnType<typeof signal<boolean>>;
  let catalogue: Product[];
  let store: ProductStore;

  beforeEach(() => {
    topic = new Subject<StockChange>();
    signedIn = signal(true);
    catalogue = [product(1, 10, 'Eau'), product(2, 3, 'Riz')];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: RealtimeService,
          useValue: {
            state: signal('online'),
            topic: (name: string) => {
              expect(name).toBe(STOCK_TOPIC);
              return topic.asObservable();
            },
          },
        },
        { provide: AuthService, useValue: { isAuthenticated: signedIn } },
        {
          provide: CatalogApi,
          useValue: {
            products: (query: ProductQuery): Observable<Product[]> =>
              of(catalogue.filter((p) => !query.lowStockOnly || p.lowStock)),
          },
        },
      ],
    });
    TestBed.inject(StockEvents);
    store = TestBed.inject(ProductStore);
  });

  function list(query: ProductQuery = {}) {
    return TestBed.runInInjectionContext(() => store.list(() => query));
  }

  it('updates the stock in every list showing the product', () => {
    const all = list();

    topic.next({ productIds: [1], products: [product(1, 4, 'Eau')] });

    expect(all.value().find((p) => p.id === 1)?.stockQuantity).toBe(4);
  });

  it('lets a product fall into the alerts the moment it runs low', () => {
    const alerts = list({ lowStockOnly: true });
    expect(alerts.value().map((p) => p.id)).toEqual([2]);

    topic.next({ productIds: [1], products: [product(1, 4, 'Eau')] });

    expect(alerts.value().map((p) => p.id)).toEqual([1, 2]);
  });

  it('adds a new product to the whole catalogue, not to a search it may not answer', () => {
    const all = list();
    const searched = list({ search: 'riz' });

    topic.next({ productIds: [9], products: [product(9, 20, 'Huile')] });

    expect(all.value().map((p) => p.name)).toEqual(['Eau', 'Huile', 'Riz']);
    expect(searched.value().map((p) => p.id)).not.toContain(9);
  });

  it('makes the lists reload when a change comes without its products', () => {
    let reloads = 0;
    store.invalidated$.subscribe(() => reloads++);

    topic.next({ productIds: [1], products: [] });

    expect(reloads).toBe(1);
  });

  it('turns a cart line short when another till sells the stock it counted on', () => {
    const cart = new Cart();
    TestBed.runInInjectionContext(() => followStock(cart));
    cart.add(product(2, 3, 'Riz'), 3);

    topic.next({ productIds: [2], products: [product(2, 1, 'Riz')] });

    expect(cart.quantityOf(product(2, 1))).toBe(3);
    expect(cart.hasShortage()).toBeTrue();
  });

  it('empties the store on sign-out', () => {
    topic.next({ productIds: [1], products: [product(1, 4)] });

    signedIn.set(false);
    TestBed.flushEffects();

    expect(store.entities().size).toBe(0);
  });
});
