import { Signal, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

/** Anything the server identifies by a numeric id. */
export interface Entity {
  readonly id: number;
}

/**
 * The one copy, in the browser, of a kind of server record — every order, every slip — keyed
 * by id.
 *
 * <p>Screens no longer each hold their own copy of an order: they hold ids (see {@link LiveList})
 * and read the order from here. So when the server says an order changed, writing it here once
 * changes it on every screen that shows it — the queue, the detail dialog, the cashier's list —
 * in the same tick, without a request.
 *
 * <p>Two ways in, and the difference matters:
 * <ul>
 *   <li>{@link load} — a list came back from the REST API. Its rows refresh what is known, but
 *       they may be older than a change pushed while the request was on its way: those are
 *       skipped, the push being the newer truth.</li>
 *   <li>{@link push} — something changed: an event from the server, or the answer to an action.
 *       Inserted when new, replaced when known, and announced on {@link pushed$} so that the
 *       lists it now belongs to can take it in.</li>
 * </ul>
 */
export class EntityStore<T extends Entity> {
  private readonly state = signal<ReadonlyMap<number, T>>(new Map());
  /** Push counter, and the count at which each entity was last pushed. */
  private sequence = 0;
  private readonly pushedAt = new Map<number, number>();
  private readonly pushes = new Subject<readonly T[]>();
  private readonly invalidations = new Subject<void>();

  /** Every entity known, by id. Read it in a `computed` to follow the changes. */
  readonly entities: Signal<ReadonlyMap<number, T>> = this.state.asReadonly();

  /** The entities that changed, as they are written — not the ones a list merely loaded. */
  readonly pushed$: Observable<readonly T[]> = this.pushes.asObservable();

  /** Asks every live list to read itself again from the server. */
  readonly invalidated$: Observable<void> = this.invalidations.asObservable();

  get(id: number): T | undefined {
    return this.state().get(id);
  }

  /** Where the push counter stands, to hand to {@link load} when the request comes back. */
  mark(): number {
    return this.sequence;
  }

  /** Ids pushed after `mark`, matching or not — for a list taking stock after a reload. */
  pushedSince(mark: number): number[] {
    const ids: number[] = [];
    this.pushedAt.forEach((at, id) => {
      if (at > mark) {
        ids.push(id);
      }
    });
    return ids;
  }

  /**
   * What a list read from the server, at a request started at `mark`. A row pushed since is kept
   * as pushed: the list was read before that change.
   */
  load(items: readonly T[], mark: number): void {
    const fresh = items.filter((item) => (this.pushedAt.get(item.id) ?? 0) <= mark);
    if (fresh.length > 0) {
      this.write(fresh);
    }
  }

  /** Inserts what is new, replaces what is known, and tells the lists. */
  push(items: readonly T[]): void {
    if (items.length === 0) {
      return;
    }
    this.sequence++;
    items.forEach((item) => this.pushedAt.set(item.id, this.sequence));
    this.write(items);
    this.pushes.next(items);
  }

  /** The pushed data is missing or may have been missed: every list reloads. */
  invalidate(): void {
    this.invalidations.next();
  }

  /** On sign-out: the next account must not see what the previous one loaded. */
  clear(): void {
    this.state.set(new Map());
    this.pushedAt.clear();
  }

  private write(items: readonly T[]): void {
    this.state.update((current) => {
      const next = new Map(current);
      items.forEach((item) => next.set(item.id, item));
      return next;
    });
  }
}
