import { DOCUMENT } from '@angular/common';
import { inject } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { filter, fromEvent, merge, pairwise } from 'rxjs';
import { NotificationType } from '../models';
import { RealtimeService } from './realtime.service';

/**
 * Mirrors OrderChangePublisher.TOPIC: an order changed — recorded, handed over, its cash brought
 * to the desk or confirmed there.
 */
export const ORDERS_TOPIC = 'orders';

/**
 * Reloads a screen's data whenever one of these notifications arrives, for as long as the
 * screen lives.
 *
 * <p>The usual way a screen uses real time: the notification is the cue, the REST API stays the
 * source. Must be called in an injection context — a constructor or a field initialiser.
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
 * Keeps a screen in step with a list the whole shop shares: reloads on every message of the
 * topic, and also whenever it may have missed some.
 *
 * <p>Nothing is queued for a screen that is not listening, so a phone that slept, or a socket that
 * dropped for a few seconds, comes back with a list that is silently out of date. Two moments
 * catch that: the socket going back online, and the page becoming visible again — the app
 * brought back to the foreground, where the socket may not yet know it is dead. Must be called in
 * an injection context.
 */
export function reloadOnTopic(resource: { reload(): void }, topic: string): void {
  const realtime = inject(RealtimeService);
  const document = inject(DOCUMENT);

  const reconnected = toObservable(realtime.state).pipe(
    pairwise(),
    filter(([before, now]) => before !== 'online' && now === 'online'),
  );
  const shownAgain = fromEvent(document, 'visibilitychange').pipe(
    filter(() => document.visibilityState === 'visible'),
  );

  merge(realtime.topic(topic), reconnected, shownAgain)
    .pipe(takeUntilDestroyed())
    .subscribe(() => resource.reload());
}
