import { Injector, runInInjectionContext, signal } from '@angular/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Observable, Subject, map, of } from 'rxjs';
import { CatalogApi } from '../api/catalog.api';
import { CatalogEntry, CatalogExplore, CatalogPage, CategoryNode, Product } from '../models';
import { ProductStore } from '../store/product.store';
import { ProductFinder, ProductQuery, SEARCH_DEBOUNCE_MS } from './product-finder';

function product(id: number, name: string): Product {
  return {
    id,
    name,
    price: 1000,
    stockQuantity: 10,
    unit: null,
    barcode: null,
    category: null,
    lowStock: false,
    stockValue: 10000,
  };
}

function rayon(id: number, name: string): CategoryNode {
  return { id, name, parentId: null, depth: 0, path: name, children: 0, imageVersion: null };
}

const item = (p: Product): CatalogEntry => ({ kind: 'product', product: p });

/** Which page the grid asks the explorer for, when, and which answer it is allowed to act on. */
describe('ProductFinder', () => {
  const best = [product(1, 'Eau Vive')];
  const boissons: CatalogEntry = { kind: 'folder', rayon: rayon(7, 'Boissons'), folders: 0, products: 12 };
  let asks: CatalogExplore[];
  /** Holds back the answer to a search's first page, to look at the grid while it is on its way. */
  let pendingSearch: Subject<Product[]> | null;
  let query: ReturnType<typeof signal<ProductQuery>>;
  let finder: ProductFinder;

  beforeEach(() => {
    asks = [];
    pendingSearch = null;
    TestBed.configureTestingModule({
      providers: [
        {
          provide: CatalogApi,
          useValue: {
            explore: (where: CatalogExplore): Observable<CatalogPage> => {
              asks.push(where);
              const page = where.page ?? 0;
              const searching = !!where.q || where.rayonId != null || !!where.favorites;
              if (!searching) {
                // The top of the shop: the main rayons, then the best sellers, one page.
                return of({ entries: [boissons, ...best.map(item)], totalProducts: 1, page: 0, hasMore: false });
              }
              // Three pages of one product each, so "show more" has something to fetch.
              const items$ = page === 0 && pendingSearch
                ? pendingSearch
                : of([product(10 + page, `Résultat ${where.q ?? ''} p${page}`)]);
              return items$.pipe(
                map((items) => ({ entries: items.map(item), totalProducts: 3, page, hasMore: page < 2 })),
              );
            },
          },
        },
      ],
    });
  });

  /** Inside each fakeAsync zone, so the debounce runs on its clock rather than a real one. */
  function start(): void {
    query = signal<ProductQuery>({ term: '', categoryId: null });
    finder = runInInjectionContext(TestBed.inject(Injector), () => new ProductFinder(query));
    TestBed.flushEffects();
    tick(SEARCH_DEBOUNCE_MS);
  }

  function ask(next: ProductQuery): void {
    query.set(next);
    TestBed.flushEffects();
    tick(SEARCH_DEBOUNCE_MS);
  }

  it('shows the main rayons as folders, then the best sellers, while nothing is asked', fakeAsync(() => {
    start();

    expect(finder.idle()).toBeTrue();
    expect(finder.folders().map((f) => f.rayon.name)).toEqual(['Boissons']);
    expect(finder.products()).toEqual(best);
  }));

  it('searches once the typing pauses, not on every key', fakeAsync(() => {
    start();
    for (const term of ['r', 'ri', 'riz']) {
      query.set({ term, categoryId: null });
      TestBed.flushEffects();
      tick(SEARCH_DEBOUNCE_MS / 3);
    }
    tick(SEARCH_DEBOUNCE_MS);

    // The top of the shop first, then a single search for the word finished.
    expect(asks.map((a) => a.q)).toEqual(['', 'riz']);
    expect(finder.products().map((p) => p.name)).toEqual(['Résultat riz p0']);
  }));

  it('offers nothing to pick from while the current query is unanswered', fakeAsync(() => {
    start();
    pendingSearch = new Subject<Product[]>();
    ask({ term: 'riz', categoryId: null });

    expect(finder.pending()).toBeTrue();
    expect(finder.products()).toEqual(best);
    expect(finder.settled()).toEqual([]);

    pendingSearch.next([product(3, 'Riz')]);
    expect(finder.settled().map((p) => p.name)).toEqual(['Riz']);
  }));

  it('opens a folder, and asks for everything below it only when told to', fakeAsync(() => {
    start();
    ask({ term: '', categoryId: 7 });
    ask({ term: '', categoryId: 7, all: true });

    expect(asks.slice(1).map((a) => [a.rayonId, !!a.all])).toEqual([
      [7, false],
      [7, true],
    ]);
  }));

  it('asks the same query again on refresh', fakeAsync(() => {
    start();
    ask({ term: '', categoryId: 4 });
    finder.refresh();

    expect(asks.slice(1).map((a) => a.rayonId)).toEqual([4, 4]);
  }));

  it('shows a stock sold at another till without asking again', fakeAsync(() => {
    start();

    TestBed.inject(ProductStore).push([{ ...best[0], stockQuantity: 0 }]);

    expect(finder.products()[0].stockQuantity).toBe(0);
    expect(asks.length).toBe(1);
  }));

  it('explores the favourites as a query of their own', fakeAsync(() => {
    start();
    ask({ term: '', categoryId: null, favorites: true });

    expect(finder.idle()).toBeFalse();
    expect(asks.at(-1)?.favorites).toBeTrue();
  }));

  it('appends the next pages, and stops when there are no more', fakeAsync(() => {
    start();
    ask({ term: 'riz', categoryId: null });

    expect(finder.total()).toBe(3);
    finder.loadMore();
    finder.loadMore();
    expect(finder.products().map((p) => p.name)).toEqual(['Résultat riz p0', 'Résultat riz p1', 'Résultat riz p2']);
    expect(finder.hasMore()).toBeFalse();

    finder.loadMore();
    expect(asks.slice(1).map((a) => a.page ?? 0)).toEqual([0, 1, 2]);
  }));

  it('drops the loaded pages when the query changes', fakeAsync(() => {
    start();
    ask({ term: 'riz', categoryId: null });
    finder.loadMore();

    ask({ term: 'eau', categoryId: null });

    expect(finder.products().map((p) => p.name)).toEqual(['Résultat eau p0']);
  }));
});
