import { inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { NotificationType } from '../models';
import { RealtimeService } from './realtime.service';

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
