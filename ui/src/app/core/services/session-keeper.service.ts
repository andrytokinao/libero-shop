import { DestroyRef, Injectable, NgZone, computed, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage.service';

/** Within this of its end, a session in use is prolonged without a word. */
export const RENEW_WITHIN_MS = 60 * 60_000;
/** Someone who touched the screen this recently is at work. */
export const ACTIVE_WITHIN_MS = 15 * 60_000;
/** Within this of its end, an idle session says so, with a button to stay. */
export const WARN_WITHIN_MS = 5 * 60_000;
const CHECK_EVERY_MS = 30_000;

/** What to do about the session now. */
export type SessionAdvice = 'renew' | 'warn' | 'none';

/**
 * The rule, apart from any timer so it can be read and tested: prolong a session that is in use
 * and nearing its end; warn about one that is idle and about to end; leave the rest alone.
 */
export function sessionAdvice(now: number, expiresAt: number | null, lastActivity: number): SessionAdvice {
  if (expiresAt === null || expiresAt <= now) {
    return 'none';
  }
  const remaining = expiresAt - now;
  if (remaining < RENEW_WITHIN_MS && now - lastActivity < ACTIVE_WITHIN_MS) {
    return 'renew';
  }
  return remaining < WARN_WITHIN_MS ? 'warn' : 'none';
}

/**
 * Keeps the session of someone at work: a cashier serving all day is not signed out in the middle
 * of a sale. While the screen is used, the session is prolonged in the background as it nears its
 * end; left idle, it warns five minutes before, with « Rester connecté ».
 *
 * <p>Started once by the application shell. The timer and the activity listeners run outside
 * Angular's zone, so they never keep a test or the page busy.
 */
@Injectable({ providedIn: 'root' })
export class SessionKeeper {
  private readonly auth = inject(AuthService);
  private readonly tokens = inject(TokenStorage);
  private readonly zone = inject(NgZone);

  private lastActivity = Date.now();
  private renewing = false;
  private readonly now = signal(Date.now());

  /** Minutes left, while the idle session is about to end; null otherwise. */
  readonly minutesLeft = computed(() => {
    const expiresAt = this.tokens.expiresAt();
    if (sessionAdvice(this.now(), expiresAt, this.lastActivity) !== 'warn' || expiresAt === null) {
      return null;
    }
    return Math.max(1, Math.ceil((expiresAt - this.now()) / 60_000));
  });

  start(destroyRef: DestroyRef): void {
    const touched = () => (this.lastActivity = Date.now());
    this.zone.runOutsideAngular(() => {
      document.addEventListener('pointerdown', touched, true);
      document.addEventListener('keydown', touched, true);
      const timer = setInterval(() => this.zone.run(() => this.check()), CHECK_EVERY_MS);
      destroyRef.onDestroy(() => {
        clearInterval(timer);
        document.removeEventListener('pointerdown', touched, true);
        document.removeEventListener('keydown', touched, true);
      });
    });
  }

  /** « Rester connecté »: the person is here. */
  stay(): void {
    this.lastActivity = Date.now();
    this.renewNow();
  }

  private check(): void {
    this.now.set(Date.now());
    if (sessionAdvice(Date.now(), this.tokens.expiresAt(), this.lastActivity) === 'renew') {
      this.renewNow();
    }
  }

  private renewNow(): void {
    if (this.renewing || this.tokens.token() === null) {
      return;
    }
    this.renewing = true;
    this.auth.renew().subscribe({
      next: () => {
        this.renewing = false;
        this.now.set(Date.now());
      },
      // Refused or unreachable: the session ends at its time, as before.
      error: () => (this.renewing = false),
    });
  }
}
