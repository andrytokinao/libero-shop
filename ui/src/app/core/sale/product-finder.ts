import { Signal, computed, inject, signal } from '@angular/core';
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
import { CatalogFolder, CatalogPage, Product } from '../models';
import { ProductStore } from '../store/product.store';

/** What a sale screen is looking for: words, a rayon, the seller's favourites — any of them, or none. */
export interface ProductQuery {
  readonly term: string;
  /** The open folder (rayon), or null for the top of the shop. */
  readonly categoryId: number | null;
  /** The pinned rayons as folders, then the pinned products. */
  readonly favorites?: boolean;
  /** In a folder: every product below it, not only those filed directly in it. */
  readonly all?: boolean;
}

/** Long enough to let a word be typed, short enough not to be noticed. */
export const SEARCH_DEBOUNCE_MS = 150;

/** One page of an answer, as the grid holds it. */
interface Page {
  /** The rayons shown as folders — on the first page only. */
  readonly folders: CatalogFolder[];
  readonly products: Product[];
  readonly total: number;
  readonly hasMore: boolean;
  readonly page: number;
}

interface Answer extends Page {
  readonly query: ProductQuery;
}

export function sameQuery(a: ProductQuery, b: ProductQuery): boolean {
  return (
    a.term === b.term &&
    a.categoryId === b.categoryId &&
    !!a.favorites === !!b.favorites &&
    !!a.all === !!b.all
  );
}

export function isIdle(query: ProductQuery): boolean {
  return !query.term && query.categoryId === null && !query.favorites;
}

/**
 * The grid of a screen that sells, fed by the server's catalogue explorer: the rayons of where
 * the seller is, as folders, then a few dozen products at a time.
 *
 * <p>At the top, with nothing typed, it shows the main rayons and the best sellers — at a till,
 * most of what is sold is the same few dozen articles. Anything else is a page the database finds,
 * counts and cuts, and {@link loadMore} appends the next one: a rayon of three hundred references
 * is reachable without the catalogue ever coming down whole.
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
  private readonly store = inject(ProductStore);
  private readonly refreshes = new Subject<void>();
  private readonly answer: Signal<Answer | null>;
  /** Pages appended by {@link loadMore} to one answer; dropped as soon as another answer lands. */
  private readonly more = signal<{ readonly to: Answer; readonly page: Page } | null>(null);

  /**
   * What the grid shows: the latest answer and the pages loaded after it, kept on screen while
   * the next answer is on its way — each product read from the store, so a stock sold at another
   * till shows here at once.
   */
  readonly products: Signal<Product[]> = computed(() => {
    const known = this.store.entities();
    const answer = this.answer();
    const more = this.more();
    const extra = more && more.to === answer ? more.page.products : [];
    return [...(answer?.products ?? []), ...extra].map((product) => known.get(product.id) ?? product);
  });

  /** The rayons the grid shows as folders, before the products — from the current answer. */
  readonly folders: Signal<CatalogFolder[]> = computed(() => this.answer()?.folders ?? []);

  /** How many products the current answer matches in all — "40 sur 130". */
  readonly total = computed(() => this.answer()?.total ?? 0);

  /** Whether a further page exists for the current answer. */
  readonly hasMore = computed(() => {
    const answer = this.answer();
    const more = this.more();
    return more && more.to === answer ? more.page.hasMore : (answer?.hasMore ?? false);
  });

  /** A further page is on its way. */
  readonly loadingMore = signal(false);

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
          this.fetch(current, 0).pipe(
            map((page): Answer => ({ query: current, ...page })),
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

  /**
   * Appends the next page of the current answer. Ignored while one is on its way, and dropped
   * if another answer lands meanwhile: it would belong to a grid no longer on screen.
   */
  loadMore(): void {
    const answer = this.answer();
    if (!answer || !this.hasMore() || this.loadingMore()) {
      return;
    }
    const current = this.more();
    const loaded = current && current.to === answer ? current.page : null;
    this.loadingMore.set(true);
    this.fetch(answer.query, (loaded?.page ?? answer.page) + 1).subscribe({
      next: (page) => {
        this.loadingMore.set(false);
        if (this.answer() !== answer) {
          return;
        }
        this.more.set({
          to: answer,
          page: { ...page, products: [...(loaded?.products ?? []), ...page.products] },
        });
      },
      error: () => this.loadingMore.set(false),
    });
  }

  /**
   * One page of the explorer — the top of the shop gets its best sellers there too — split into
   * its folders and its products, the products written into the store as they arrive.
   */
  private fetch(query: ProductQuery, page: number): Observable<Page> {
    return this.store
      .readCatalog(
        this.catalog.explore({
          q: query.term,
          rayonId: query.categoryId,
          all: query.all,
          favorites: query.favorites,
          page,
          size: this.limit,
        }),
      )
      .pipe(map((found) => toPage(found, page)));
  }
}

function toPage(found: CatalogPage, page: number): Page {
  const folders: CatalogFolder[] = [];
  const products: Product[] = [];
  for (const entry of found.entries) {
    if (entry.kind === 'folder') {
      folders.push(entry);
    } else {
      products.push(entry.product);
    }
  }
  return { folders, products, total: found.totalProducts, hasMore: found.hasMore, page };
}
