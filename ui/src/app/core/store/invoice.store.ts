import { Injectable, inject } from '@angular/core';
import { MonoTypeOperatorFunction, Observable, tap } from 'rxjs';
import { InvoiceApi, InvoiceQuery } from '../api/invoice.api';
import {
  CancelInvoiceRequest,
  CreateSaleRequest,
  DeliveryResult,
  Invoice,
  PayInvoiceRequest,
} from '../models';
import { AuthService } from '../services/auth.service';
import { isoDate } from '../utils/date.util';
import { EntityStore } from './entity-store';
import { QueryContext, matchesInvoiceQuery, newestInvoiceFirst } from './invoice-query';
import { LiveList, liveList } from './live-list';

/**
 * Every order the browser knows, once, and the only way the screens change one.
 *
 * <p>Reads: {@link list} gives a screen its live list for a query. Writes: the server's events
 * come in through {@link OrderEvents}; the actions taken here — hand over, pay, cancel — go
 * through the methods below, which read the order back once the server has agreed. The action's
 * own answer is not used for that: it does not say where the cash is, and the socket's echo
 * may not come if the connection has dropped.
 */
@Injectable({ providedIn: 'root' })
export class InvoiceStore extends EntityStore<Invoice> {
  private readonly api = inject(InvoiceApi);
  private readonly auth = inject(AuthService);

  /**
   * The orders answering `query`, kept current. The query may read signals — a search box — and
   * is read again at each {@link LiveList.reload}. Must be called in an injection context.
   */
  list(query: () => InvoiceQuery): LiveList<Invoice> {
    return liveList(this, {
      load: () => this.api.search(query()),
      matches: (invoice) => matchesInvoiceQuery(invoice, query(), this.context()),
      compare: newestInvoiceFirst,
    });
  }

  /** Reads these orders again and pushes them to every screen. */
  refresh(ids: readonly number[]): void {
    new Set(ids).forEach((id) => this.api.byId(id).subscribe((invoice) => this.push([invoice])));
  }

  createSale(request: CreateSaleRequest): Observable<Invoice> {
    return this.api.createSale(request).pipe(this.thenRefresh((invoice) => invoice.id));
  }

  take(invoiceId: number): Observable<Invoice> {
    return this.api.take(invoiceId).pipe(this.thenRefresh(() => invoiceId));
  }

  release(invoiceId: number): Observable<Invoice> {
    return this.api.release(invoiceId).pipe(this.thenRefresh(() => invoiceId));
  }

  deliver(invoiceId: number, collect = true): Observable<DeliveryResult> {
    return this.api.deliver(invoiceId, collect).pipe(this.thenRefresh(() => invoiceId));
  }

  collect(invoiceId: number): Observable<Invoice> {
    return this.api.collect(invoiceId).pipe(this.thenRefresh(() => invoiceId));
  }

  pay(invoiceId: number, request: PayInvoiceRequest): Observable<Invoice> {
    return this.api.pay(invoiceId, request).pipe(this.thenRefresh(() => invoiceId));
  }

  cancel(invoiceId: number, request: CancelInvoiceRequest): Observable<Invoice> {
    return this.api.cancel(invoiceId, request).pipe(this.thenRefresh(() => invoiceId));
  }

  print(invoiceId: number): Observable<Invoice> {
    return this.api.print(invoiceId).pipe(this.thenRefresh(() => invoiceId));
  }

  private thenRefresh<R>(idOf: (result: R) => number): MonoTypeOperatorFunction<R> {
    return tap((result) => this.refresh([idOf(result)]));
  }

  private context(): QueryContext {
    return { userId: this.auth.currentUser()?.id ?? null, today: isoDate(new Date()) };
  }
}
