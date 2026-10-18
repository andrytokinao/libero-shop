import { Injectable, WritableSignal, effect, inject, untracked } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { Cart, CartLine } from './cart';
import { HELD_SALES_STORAGE } from './held-sales';

/** Which screen's basket: the till's and the order-taking's are kept apart. */
export type CartDraftScope = 'caisse' | 'commande';

/** The basket being served, as it stood a moment ago. */
export interface CartDraft {
  readonly clientName: string;
  readonly lines: readonly CartLine[];
}

const KEY_PREFIX = 'liberoshop.cart-draft.';

/**
 * The basket being served, kept as it changes — so a page reloaded by mistake, a click on the menu
 * or a session that expired and was signed into again does not lose the customer's articles.
 *
 * <p>Kept like the held sales ({@link HELD_SALES_STORAGE}): in the tab's storage, per signed-in
 * user, so it survives a reload but not the till session, and a colleague signing in at the same
 * till does not inherit it. The server prices and checks the stock again when the sale is sent.
 */
@Injectable({ providedIn: 'root' })
export class CartDrafts {
  private readonly storage = inject(HELD_SALES_STORAGE);
  private readonly auth = inject(AuthService);

  read(scope: CartDraftScope): CartDraft | null {
    try {
      const raw = this.storage.getItem(this.key(scope));
      const draft = raw ? (JSON.parse(raw) as CartDraft) : null;
      return draft?.lines?.length ? draft : null;
    } catch {
      // A corrupted entry must not take the till down with it.
      return null;
    }
  }

  /** An empty basket leaves nothing behind. */
  write(scope: CartDraftScope, draft: CartDraft): void {
    if (draft.lines.length) {
      this.storage.setItem(this.key(scope), JSON.stringify(draft));
    } else {
      this.storage.removeItem(this.key(scope));
    }
  }

  private key(scope: CartDraftScope): string {
    return `${KEY_PREFIX}${scope}.${this.auth.currentUser()?.username ?? 'anonyme'}`;
  }
}

/**
 * Brings back the basket left on this screen, if any, then keeps it as it changes. Must be called
 * in an injection context — the component's constructor.
 */
export function keepCartDraft(scope: CartDraftScope, cart: Cart, clientName: WritableSignal<string>): void {
  const drafts = inject(CartDrafts);
  const toasts = inject(ToastService);
  const left = drafts.read(scope);
  if (left && cart.isEmpty()) {
    cart.restore(left.lines);
    clientName.set(left.clientName);
    toasts.show(`Panier en cours retrouvé : ${left.lines.length} article(s).`);
  }
  effect(() => {
    const draft = { clientName: clientName(), lines: cart.lines() };
    untracked(() => drafts.write(scope, draft));
  });
}
