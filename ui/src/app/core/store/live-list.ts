import { DestroyRef, Signal, computed, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription } from 'rxjs';
import { Entity, EntityStore } from './entity-store';

export interface LiveListOptions<T extends Entity> {
  /** The server's answer for the list — its search and filters. */
  readonly load: () => Observable<readonly T[]>;
  /**
   * Whether an entity belongs to the list: the server's filters, said again in the browser. What
   * lets an order pushed by the server join the lists it belongs to and leave the others.
   * Signals read here — a search box — are followed.
   */
  readonly matches: (item: T) => boolean;
  /**
   * Whether a pushed entity the server did not answer may join. Defaults to `matches`; a query
   * the browser cannot say again — a search through the rayon tree — answers false, and a new
   * entity waits for the next reload. The rows already there still follow every push.
   */
  readonly admits?: (item: T) => boolean;
  /** The order of the rows, the same as the server's. */
  readonly compare: (a: T, b: T) => number;
}

/**
 * A screen's list of server records, kept current by the store rather than by reloading.
 *
 * <p>It holds ids, not records: the rows are read from the {@link EntityStore} each time, so a
 * change pushed there shows here at once. Its members are what the server answered, plus every
 * entity pushed since that {@link LiveListOptions.matches matches} — the new sale joins the
 * depot's queue — and a member that stops matching drops out of the rows — the order handed over
 * leaves it.
 *
 * <p>Same face as {@link ApiResource} — `value`, `loading`, `reload()` — so a screen moves from
 * one to the other without its template noticing.
 */
export class LiveList<T extends Entity> {
  private readonly members = signal<ReadonlySet<number>>(new Set());
  private readonly inFlight = signal(false);
  private request?: Subscription;

  readonly loading: Signal<boolean> = this.inFlight.asReadonly();

  readonly value: Signal<T[]> = computed(() => {
    const entities = this.store.entities();
    const rows: T[] = [];
    this.members().forEach((id) => {
      const entity = entities.get(id);
      if (entity && this.options.matches(entity)) {
        rows.push(entity);
      }
    });
    return rows.sort(this.options.compare);
  });

  constructor(
    private readonly store: EntityStore<T>,
    private readonly options: LiveListOptions<T>,
    destroyRef: DestroyRef,
  ) {
    store.pushed$.pipe(takeUntilDestroyed(destroyRef)).subscribe((items) => this.take(items));
    store.invalidated$.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => this.reload());
    destroyRef.onDestroy(() => this.request?.unsubscribe());
    this.reload();
  }

  /**
   * Asks the server again — after the search changed, or when pushes may have been missed. A
   * newer reload cancels the one still on its way. A failure keeps the rows in place: the error
   * interceptor has told the user.
   */
  reload(): void {
    this.request?.unsubscribe();
    const mark = this.store.mark();
    this.inFlight.set(true);
    this.request = this.options.load().subscribe({
      next: (items) => {
        this.store.load(items, mark);
        // What changed during the request is the store's newer version; whether it belongs
        // here is decided on that version, not on the list's.
        const pushed = this.store
          .pushedSince(mark)
          .map((id) => this.store.get(id))
          .filter((item): item is T => item !== undefined);
        this.members.set(new Set([...items.map((item) => item.id), ...this.matching(pushed)]));
        this.inFlight.set(false);
      },
      error: () => this.inFlight.set(false),
    });
  }

  /** Pushed entities that belong here join; the others are left to `matches` to hide. */
  private take(items: readonly T[]): void {
    const joining = this.matching(items).filter((id) => !this.members().has(id));
    if (joining.length > 0) {
      this.members.update((current) => new Set([...current, ...joining]));
    }
  }

  private matching(items: readonly T[]): number[] {
    const admits = this.options.admits ?? this.options.matches;
    return untracked(() => items.filter((item) => admits(item)).map((item) => item.id));
  }
}

/**
 * A {@link LiveList} that lives as long as the screen creating it. Must be called in an
 * injection context — a field initializer or a constructor.
 */
export function liveList<T extends Entity>(
  store: EntityStore<T>,
  options: LiveListOptions<T>,
): LiveList<T> {
  return new LiveList(store, options, inject(DestroyRef));
}
