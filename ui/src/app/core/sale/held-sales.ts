import { Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { Cart, CartLine } from './cart';

/** A customer's basket set aside — they forgot something — while the next one is served. */
export interface HeldSale {
  readonly id: string;
  readonly clientName: string;
  /** Epoch milliseconds, for "en attente depuis 14:32". */
  readonly heldAt: number;
  readonly lines: readonly CartLine[];
}

/**
 * Where held sales are kept. The tab's `sessionStorage` by default: a held basket must survive
 * a page reloaded by mistake, but not outlive the till session it belongs to — the prices and
 * stock it carries would be stale by the next day. A token so tests can hand in their own.
 */
export const HELD_SALES_STORAGE = new InjectionToken<Storage>('HELD_SALES_STORAGE', {
  providedIn: 'root',
  factory: () => sessionStorage,
});

const KEY_PREFIX = 'liberoshop.held-sales.';

/**
 * The sales a till has put on hold, per signed-in user.
 *
 * <p>Keyed by username, so the next cashier signing in at the same till does not find their
 * colleague's customers in their list. Storage is the source of truth: {@link list} re-reads it
 * whenever this service writes or the user changes, so there is no second copy to keep in step.
 *
 * <p>The products are kept as they were when the sale was held. The server prices and checks
 * the stock again when the sale is finally recorded, so a stale snapshot can at worst be
 * refused there, never sold wrong.
 */
@Injectable({ providedIn: 'root' })
export class HeldSales {
  private readonly storage = inject(HELD_SALES_STORAGE);
  private readonly auth = inject(AuthService);
  private readonly writes = signal(0);
  private sequence = 0;

  private readonly key = computed(
    () => KEY_PREFIX + (this.auth.currentUser()?.username ?? 'anonyme'),
  );

  /** Oldest first: the customer who has waited longest is served back first. */
  readonly list = computed<readonly HeldSale[]>(() => {
    this.writes();
    return this.read(this.key());
  });

  readonly count = computed(() => this.list().length);

  /** Sets the cart's content aside under the client's name, and empties the cart. */
  hold(cart: Cart, clientName: string): HeldSale | null {
    if (cart.isEmpty()) {
      return null;
    }
    const held: HeldSale = {
      // Not crypto.randomUUID(): it only exists in secure contexts, and a till reached over the
      // shop's Wi-Fi by plain HTTP is not one. Unique within a tab is all this needs.
      id: `${Date.now().toString(36)}-${(this.sequence++).toString(36)}`,
      clientName: clientName.trim(),
      heldAt: Date.now(),
      lines: cart.lines(),
    };
    this.write([...this.list(), held]);
    cart.clear();
    return held;
  }

  /** Removes a held sale from the list and hands it back, or null when it is gone already. */
  take(id: string): HeldSale | null {
    const held = this.list().find((sale) => sale.id === id) ?? null;
    if (held) {
      this.write(this.list().filter((sale) => sale.id !== id));
    }
    return held;
  }

  private read(key: string): HeldSale[] {
    try {
      const raw = this.storage.getItem(key);
      return raw ? (JSON.parse(raw) as HeldSale[]) : [];
    } catch {
      // A corrupted entry must not take the till down with it.
      return [];
    }
  }

  private write(sales: readonly HeldSale[]): void {
    if (sales.length) {
      this.storage.setItem(this.key(), JSON.stringify(sales));
    } else {
      this.storage.removeItem(this.key());
    }
    this.writes.update((n) => n + 1);
  }
}
