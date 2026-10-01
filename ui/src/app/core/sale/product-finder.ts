import { Signal, computed, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  merge,
  switchMap,
} from 'rxjs';
import { CatalogApi } from '../api/catalog.api';
import { Product } from '../models';

/** What a sale screen is looking for: words, a rayon, both or neither. */
export interface ProductQuery {
  readonly term: string;
  readonly categoryId: number | null;
}

/** Long enough to let a word be typed, short enough not to be noticed. */
export const SEARCH_DEBOUNCE_MS = 150;

interface Answer {
  readonly query: ProductQuery;
  readonly products: Product[];
}

export function sameQuery(a: ProductQuery, b: ProductQuery): boolean {
  return a.term === b.term && a.categoryId === b.categoryId;
}

export function isIdle(query: ProductQuery): boolean {
  return !query.term && query.categoryId === null;
}

/**
 * The grid of a screen that sells, fed by the server a few dozen products at a time.
 *
 * <p>With nothing typed and no rayon picked it shows the best sellers — at a till, most of what
 * is sold is the same few dozen articles. Anything else is a bounded search the database runs.
 * Either way the catalogue never comes down whole, so a shop of ten thousand references sells as
 * fast as one of fifty.
 *
 * <p>The query is debounced, and a newer one cancels the request of an older one (`switchMap`),
 * so a fast typist never sees the answer to a word they have already finished. Every answer
 * carries the query it answers, which is what {@link settled} relies on.
 *
 * <p>Built in a component's field initializer, like {@link Cart}: it injects what it needs, and
 * each screen owns its own.
 */
export class ProductFinder {
  private readonly catalog = inject(CatalogApi);
  private readonly refreshes = new Subject<void>();
  private readonly answer: Signal<Answer | null>;

  /** What the grid shows: the latest answer, kept on screen while the next one is on its way. */
  readonly products: Signal<Product[]> = computed(() => this.answer()?.products ?? []);

  /** True while the grid shows best sellers rather than search results. */
  readonly idle = computed(() => isIdle(this.query()));

  /** The current query has no answer yet. */
  readonly pending = computed(() => {
    const answer = this.answer();
    return !answer || !sameQuery(answer.query, this.query());
  });

  /**
   * The products found for exactly the current query, or none while it is still on its way.
   * What Enter may pick from: acting on the answer to an older query would add the wrong article.
   */
  readonly settled: Signal<readonly Product[]> = computed(() =>
    this.pending() ? [] : this.products(),
  );

  constructor(
    private readonly query: Signal<ProductQuery>,
    private readonly limit?: number,
  ) {
    const typed$ = toObservable(query).pipe(
      debounceTime(SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(sameQuery),
    );
    const refreshed$ = this.refreshes.pipe(map(() => this.query()));

    this.answer = toSignal(
      merge(typed$, refreshed$).pipe(
        switchMap((current) =>
          this.fetch(current).pipe(
            map((products): Answer => ({ query: current, products })),
            // The interceptor has said why; the grid keeps its last answer.
            catchError(() => EMPTY),
          ),
        ),
      ),
      { initialValue: null },
    );
  }

  /** Asks the current query again — after a sale, for the stock figures it moved. */
  refresh(): void {
    this.refreshes.next();
  }

  private fetch(query: ProductQuery): Observable<Product[]> {
    return isIdle(query)
      ? this.catalog.featuredProducts(this.limit)
      : this.catalog.searchProducts(query.term, query.categoryId, this.limit);
  }
}
