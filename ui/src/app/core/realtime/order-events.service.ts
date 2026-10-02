import { Injectable, effect, inject, untracked } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { OrderChange, OrderChangeKind } from '../models';
import { AuthService } from '../services/auth.service';
import { InvoiceStore } from '../store/invoice.store';
import { RemittanceStore } from '../store/remittance.store';
import { RealtimeService } from './realtime.service';
import { Resync } from './resync.service';

/** Mirrors OrderChangePublisher.TOPIC. */
export const ORDERS_TOPIC = 'orders';

/** What one kind of change does to the browser's data. */
type OrderChangeHandler = (change: OrderChange) => void;

/**
 * The one listener of the order events, and the one place that decides what each does to the
 * data the screens show.
 *
 * <p>Before, every screen subscribed to the topic and reloaded its own list: ten screens, ten
 * requests per sale. Now the event is applied once, here, to the stores — {@link InvoiceStore},
 * {@link RemittanceStore} — and every screen reading them follows in the same tick. A screen
 * neither subscribes to the socket nor knows what an event means.
 *
 * <p>One handler per kind of change, in {@link handlers} (Strategy): a new kind on the server is
 * one more line there, and nothing in the screens. A kind this version does not know — a newer
 * server — and a change that came without its data both fall back to every list reloading.
 *
 * <p>Also catches what was missed: at each {@link Resync} moment — back online, back on screen —
 * the lists reload. On sign-out,
 * the stores are emptied — the next account must not see what this one loaded.
 *
 * <p>Started once, by the application's initializer; nothing else needs to inject it, except the
 * figures computed on the server — dashboards, cash in hand — which cannot be patched and reload
 * on {@link changes$} through `reloadOnOrderChange`.
 */
@Injectable({ providedIn: 'root' })
export class OrderEvents {
  private readonly realtime = inject(RealtimeService);
  private readonly invoices = inject(InvoiceStore);
  private readonly remittances = inject(RemittanceStore);
  private readonly applied = new Subject<OrderChange>();

  private readonly handlers: Record<OrderChangeKind, OrderChangeHandler> = {
    // A new order: joins every list whose query it answers — the depot queue, "Mes factures".
    [OrderChangeKind.ADDED]: (change) => this.putOrders(change),
    // A known order moved on: replaced where it is shown, and gone from the lists it no
    // longer answers — handed over, it leaves the queue.
    [OrderChangeKind.DELIVERED]: (change) => this.putOrders(change),
    [OrderChangeKind.PAID]: (change) => this.putOrders(change),
    [OrderChangeKind.CANCELLED]: (change) => this.putOrders(change),
    [OrderChangeKind.PRINTED]: (change) => this.putOrders(change),
    // Someone took an order on, or gave it back: every queue shows who is on it.
    [OrderChangeKind.HANDLING]: (change) => this.putOrders(change),
    // The cash moved: the slip and the orders it carries, both.
    [OrderChangeKind.CASH_REMITTED]: (change) => this.putCash(change),
    [OrderChangeKind.CASH_CONFIRMED]: (change) => this.putCash(change),
  };

  /** Every change, once applied to the stores. */
  readonly changes$: Observable<OrderChange> = this.applied.asObservable();

  constructor() {
    this.realtime.topic<OrderChange>(ORDERS_TOPIC).subscribe((change) => this.apply(change));
    inject(Resync).resync$.subscribe(() => this.reloadAll());

    const auth = inject(AuthService);
    effect(() => {
      if (!auth.isAuthenticated()) {
        untracked(() => {
          this.invoices.clear();
          this.remittances.clear();
        });
      }
    });
  }

  /** Applies one change to the stores. Public for the tests; the topic is its only caller. */
  apply(change: OrderChange): void {
    const handler = this.handlers[change.change] as OrderChangeHandler | undefined;
    if (handler) {
      handler(change);
    } else {
      this.reloadAll();
    }
    this.applied.next(change);
  }

  private putOrders(change: OrderChange): void {
    if (change.invoices?.length) {
      this.invoices.push(change.invoices);
    } else {
      // Sent without the orders: the server could not read them back.
      this.invoices.invalidate();
    }
  }

  private putCash(change: OrderChange): void {
    this.putOrders(change);
    if (change.remittance) {
      this.remittances.push([change.remittance]);
    } else {
      this.remittances.invalidate();
    }
  }

  private reloadAll(): void {
    this.invoices.invalidate();
    this.remittances.invalidate();
  }
}
