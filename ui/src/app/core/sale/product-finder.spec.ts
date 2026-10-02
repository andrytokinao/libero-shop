import { Injector, runInInjectionContext, signal } from '@angular/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Observable, Subject, of } from 'rxjs';
import { CatalogApi } from '../api/catalog.api';
import { Product } from '../models';
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

/** Which endpoint the grid asks, when, and which answer it is allowed to act on. */
describe('ProductFinder', () => {
  const best = [product(1, 'Eau Vive')];
  let searches: { term: string; categoryId: number | null }[];
  let pendingSearch: Subject<Product[]> | null;
  let query: ReturnType<typeof signal<ProductQuery>>;
  let finder: ProductFinder;

  beforeEach(() => {
    searches = [];
    pendingSearch = null;
    TestBed.configureTestingModule({
      providers: [
        {
          provide: CatalogApi,
          useValue: {
            featuredProducts: (): Observable<Product[]> => of(best),
            searchProducts: (term: string, categoryId: number | null): Observable<Product[]> => {
              searches.push({ term, categoryId });
              return pendingSearch ?? of([product(2, `Résultat ${term}`)]);
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

  it('shows the best sellers while nothing is asked', fakeAsync(() => {
    start();

    expect(finder.idle()).toBeTrue();
    expect(finder.products()).toEqual(best);
    expect(searches).toEqual([]);
  }));

  it('searches once the typing pauses, not on every key', fakeAsync(() => {
    start();
    for (const term of ['r', 'ri', 'riz']) {
      query.set({ term, categoryId: null });
      TestBed.flushEffects();
      tick(SEARCH_DEBOUNCE_MS / 3);
    }
    tick(SEARCH_DEBOUNCE_MS);

    expect(searches).toEqual([{ term: 'riz', categoryId: null }]);
    expect(finder.products().map((p) => p.name)).toEqual(['Résultat riz']);
  }));

  it('offers nothing to pick from while the current query is unanswered', fakeAsync(() => {
    start();
    pendingSearch = new Subject<Product[]>();
    query.set({ term: 'riz', categoryId: null });
    TestBed.flushEffects();
    tick(SEARCH_DEBOUNCE_MS);

    expect(finder.pending()).toBeTrue();
    expect(finder.products()).toEqual(best);
    expect(finder.settled()).toEqual([]);

    pendingSearch.next([product(3, 'Riz')]);
    expect(finder.settled().map((p) => p.name)).toEqual(['Riz']);
  }));

  it('asks the same query again on refresh', fakeAsync(() => {
    start();
    query.set({ term: '', categoryId: 4 });
    TestBed.flushEffects();
    tick(SEARCH_DEBOUNCE_MS);
    finder.refresh();

    expect(searches).toEqual([
      { term: '', categoryId: 4 },
      { term: '', categoryId: 4 },
    ]);
  }));

  it('shows a stock sold at another till without asking again', fakeAsync(() => {
    start();

    TestBed.inject(ProductStore).push([{ ...best[0], stockQuantity: 0 }]);

    expect(finder.products()[0].stockQuantity).toBe(0);
    expect(searches).toEqual([]);
  }));
});
