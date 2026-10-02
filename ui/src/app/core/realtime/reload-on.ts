import { inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, merge } from 'rxjs';
import { NotificationType } from '../models';
import { OrderEvents } from './order-events.service';
import { RealtimeService } from './realtime.service';

/**
 * Reloads a screen's data whenever one of these notifications arrives, for as long as the
 * screen lives. Must be called in an injection context — a constructor or a field initialiser.
 */
export function reloadOn(resource: { reload(): void }, ...types: NotificationType[]): void {
  inject(RealtimeService)
    .notifications$.pipe(
      filter((notification) => types.includes(notification.type)),
      takeUntilDestroyed(),
    )
    .subscribe(() => resource.reload());
}

/**
 * Reloads figures the server computes from the orders — a dashboard's totals, the cash in hand —
 * whenever an order changes, and whenever changes may have been missed.
 *
 * <p>Only for what cannot be patched in the browser. A list of orders or of slips reads the
 * stores instead (`InvoiceStore.list`, `RemittanceStore.list`), which {@link OrderEvents} keeps
 * current without a request. Must be called in an injection context.
 */
export function reloadOnOrderChange(resource: { reload(): void }): void {
  const events = inject(OrderEvents);
  merge(events.changes$, events.resync$)
    .pipe(takeUntilDestroyed())
    .subscribe(() => resource.reload());
}
