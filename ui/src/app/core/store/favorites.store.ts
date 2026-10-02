import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Observable } from 'rxjs';
import { Favorites, FavoriteApi } from '../api/favorite.api';
import { AuthService } from '../services/auth.service';

/**
 * The signed-in seller's favourites, held once for every screen that sells: a star lit on the
 * till's tile is lit on the order screen too, without either asking the server again.
 *
 * <p>Re-read whenever the account changes — favourites are personal — and emptied on sign-out.
 * A tap on a star answers at once and the server's answer then replaces the guess; a refusal
 * puts the star back, the interceptor having said why.
 */
@Injectable({ providedIn: 'root' })
export class FavoritesStore {
  private readonly api = inject(FavoriteApi);
  private readonly auth = inject(AuthService);
  private readonly state = signal<Favorites>({ productIds: [], categoryIds: [] });

  private readonly products = computed(() => new Set(this.state().productIds));
  private readonly categories = computed(() => new Set(this.state().categoryIds));

  /** Whether the seller pinned anything — the "Favoris" chip only shows then. */
  readonly any = computed(() => this.state().productIds.length + this.state().categoryIds.length > 0);

  constructor() {
    effect(() => {
      const username = this.auth.currentUser()?.username;
      // Written outside the tracking, like the other stores: the effect only listens to the account.
      untracked(() => {
        this.state.set({ productIds: [], categoryIds: [] });
        if (username) {
          this.api.mine().subscribe((favorites) => this.state.set(favorites));
        }
      });
    });
  }

  isProductPinned(id: number): boolean {
    return this.products().has(id);
  }

  isCategoryPinned(id: number): boolean {
    return this.categories().has(id);
  }

  toggleProduct(id: number): void {
    this.toggle('products', id, this.isProductPinned(id));
  }

  toggleCategory(id: number): void {
    this.toggle('categories', id, this.isCategoryPinned(id));
  }

  private toggle(kind: 'products' | 'categories', id: number, pinned: boolean): void {
    const before = this.state();
    const key = kind === 'products' ? 'productIds' : 'categoryIds';
    this.state.set({
      ...before,
      [key]: pinned ? before[key].filter((other) => other !== id) : [id, ...before[key]],
    });
    const call: Observable<Favorites> = pinned ? this.api.unpin(kind, id) : this.api.pin(kind, id);
    call.subscribe({
      next: (favorites) => this.state.set(favorites),
      error: () => this.state.set(before),
    });
  }
}
