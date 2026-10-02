import { Injectable, effect, inject, untracked } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { StockChange } from '../models';
import { AuthService } from '../services/auth.service';
import { ProductStore } from '../store/product.store';
import { RealtimeService } from './realtime.service';
import { Resync } from './resync.service';

/** Mirrors StockChangePublisher.TOPIC. */
export const STOCK_TOPIC = 'stock';

/**
 * The one listener of the stock events: the counterpart of {@link OrderEvents} for the catalogue.
 *
 * <p>A stock that moved — a sale at another till, a delivery received, an order cancelled — is
 * written once to the {@link ProductStore}, and every grid, cart and stock table reading it
 * follows. A change that came without its products, and every {@link Resync} moment, make the
 * product lists reload; signing out empties the store.
 *
 * <p>Started once with the application shell. The figures the server computes from the stock —
 * the depot's value, the alerts count — reload on {@link changes$} through `reloadOnStockChange`.
 */
@Injectable({ providedIn: 'root' })
export class StockEvents {
  private readonly products = inject(ProductStore);
  private readonly applied = new Subject<StockChange>();

  /** Every change, once applied to the store. */
  readonly changes$: Observable<StockChange> = this.applied.asObservable();

  constructor() {
    inject(RealtimeService)
      .topic<StockChange>(STOCK_TOPIC)
      .subscribe((change) => this.apply(change));
    inject(Resync).resync$.subscribe(() => this.products.invalidate());

    const auth = inject(AuthService);
    effect(() => {
      if (!auth.isAuthenticated()) {
        untracked(() => this.products.clear());
      }
    });
  }

  /** Applies one change to the store. Public for the tests; the topic is its only caller. */
  apply(change: StockChange): void {
    if (change.products?.length) {
      this.products.push(change.products);
    } else {
      // Sent without the products: the server could not read them back.
      this.products.invalidate();
    }
    this.applied.next(change);
  }
}
