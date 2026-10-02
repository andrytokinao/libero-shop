import { Injectable, inject } from '@angular/core';
import { Observable, defer, tap } from 'rxjs';
import { CatalogApi } from '../api/catalog.api';
import { Product } from '../models';
import { EntityStore } from './entity-store';
import { LiveList, liveList } from './live-list';

/** Mirrors the parameters of `GET /api/products`. */
export interface ProductQuery {
  readonly search?: string;
  /** A rayon and everything below it. */
  readonly categoryId?: number | null;
  readonly lowStockOnly?: boolean;
}

/**
 * Every product the browser knows, once, with its stock as last heard — what turns an article
 * red at one till the moment another sells the last of it.
 *
 * <p>Written by {@link StockEvents} when the server says a stock moved, and by every list of
 * products read: the stock screens through {@link list}, the sale screens' searches through
 * {@link read}.
 */
@Injectable({ providedIn: 'root' })
export class ProductStore extends EntityStore<Product> {
  private readonly api = inject(CatalogApi);

  /**
   * The products answering `query`, their stock kept current. A new product joins only the
   * unfiltered list: the search and the rayon tree are the server's to resolve. Must be called in
   * an injection context.
   */
  list(query: () => ProductQuery = () => ({})): LiveList<Product> {
    const matches = (product: Product) => !query().lowStockOnly || product.lowStock;
    return liveList(this, {
      load: () => this.api.products(query()),
      matches,
      admits: (product) => !query().search?.trim() && query().categoryId == null && matches(product),
      // The server's order: by name.
      compare: (a, b) => a.name.localeCompare(b.name, 'fr'),
    });
  }

  /**
   * A list of products read elsewhere — a sale screen's search — written in as it arrives, so the
   * store knows them and a change pushed meanwhile is not set back by it.
   */
  read(request: Observable<Product[]>): Observable<Product[]> {
    return defer(() => {
      const mark = this.mark();
      return request.pipe(tap((products) => this.load(products, mark)));
    });
  }
}
