import { Signal, WritableSignal, signal } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * A remote collection or object held in a signal, with a way to ask for it again.
 *
 * <p>Every screen shows server state that another screen can change — a sale made at the
 * desk appears in the depot's queue — so each of them needs the same three things: the
 * current value, whether a refresh is in flight, and a `reload()` to call after a
 * mutation. Rather than repeat that subscribe/assign dance in nineteen components, it
 * lives here once.
 */
export class ApiResource<T> {
  private readonly state: WritableSignal<T>;
  private readonly inFlight = signal(false);

  readonly value: Signal<T>;
  readonly loading: Signal<boolean> = this.inFlight.asReadonly();

  constructor(
    initial: T,
    private readonly loader: () => Observable<T>,
  ) {
    this.state = signal(initial);
    this.value = this.state.asReadonly();
    this.reload();
  }

  /**
   * Re-fetches. A failure leaves the previous value in place: the error interceptor has
   * already told the user, and blanking the screen on a hiccup would be worse than
   * showing figures that are a few seconds old.
   */
  reload(): void {
    this.inFlight.set(true);
    this.loader().subscribe({
      next: (value) => {
        this.state.set(value);
        this.inFlight.set(false);
      },
      error: () => this.inFlight.set(false),
    });
  }
}

/** Reads better at the call site than `new ApiResource(...)`. */
export function apiResource<T>(initial: T, loader: () => Observable<T>): ApiResource<T> {
  return new ApiResource(initial, loader);
}
