import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { Observable, filter, fromEvent, map, merge, pairwise, share } from 'rxjs';
import { RealtimeService } from './realtime.service';

/**
 * The moments when pushed changes may have been missed, for every store to read itself again.
 *
 * <p>Nothing is queued for a screen that is not listening: a phone that slept, or a socket that
 * dropped for a few seconds, comes back with data that is silently out of date. Two moments catch
 * that — the socket going back online, and the page becoming visible again, the app brought back
 * to the foreground, where the socket may not yet know it is dead.
 */
@Injectable({ providedIn: 'root' })
export class Resync {
  readonly resync$: Observable<void>;

  constructor() {
    const realtime = inject(RealtimeService);
    const document = inject(DOCUMENT);
    const reconnected = toObservable(realtime.state).pipe(
      pairwise(),
      filter(([before, now]) => before !== 'online' && now === 'online'),
    );
    const shownAgain = fromEvent(document, 'visibilitychange').pipe(
      filter(() => document.visibilityState === 'visible'),
    );
    this.resync$ = merge(reconnected, shownAgain).pipe(
      map(() => undefined),
      share(),
    );
  }
}
