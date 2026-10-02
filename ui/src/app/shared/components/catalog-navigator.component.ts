import { Component, computed, inject, input, output } from '@angular/core';
import { CategoryNode } from '../../core/models';
import { FavoritesStore } from '../../core/store/favorites.store';

/** Where a sale screen's grid is: the top of the shop, the seller's favourites, or inside a rayon. */
export type CatalogScope =
  | { readonly kind: 'featured' }
  | { readonly kind: 'favorites' }
  /** `all`: every product below the rayon, not only those filed directly in it. */
  | { readonly kind: 'rayon'; readonly id: number; readonly all?: boolean };

export const FEATURED: CatalogScope = { kind: 'featured' };

/**
 * The path above a sale screen's grid once the seller has gone into a folder — "Tous › Épicerie
 * › Riz" — each step a way back up, with the star that pins the open rayon and the switch between
 * what is filed in it and everything below it.
 *
 * <p>Nothing at the top of the shop: there the grid itself is the way in, folders first. The
 * screens keep their header to the search field alone.
 *
 * <p>It only says where the grid is ({@link scopeChange}); the screen owns the grid and asks the
 * server. Shared by the till and the order screen, so both move the same way.
 */
@Component({
  selector: 'app-catalog-navigator',
  standalone: true,
  template: `
    @if (scope().kind !== 'featured') {
      <nav class="cn-path" aria-label="Emplacement dans le catalogue">
        <button type="button" class="cn-crumb" (click)="go(featured)">‹ Tous</button>
        @if (scope().kind === 'favorites') {
          <span class="cn-sep" aria-hidden="true">›</span>
          <strong class="cn-here">★ Favoris</strong>
        }
        @for (node of path(); track node.id; let last = $last) {
          <span class="cn-sep" aria-hidden="true">›</span>
          @if (last) {
            <strong class="cn-here">{{ node.name }}</strong>
            <button
              type="button"
              class="cn-pin"
              [class.on]="favorites.isCategoryPinned(node.id)"
              [attr.aria-pressed]="favorites.isCategoryPinned(node.id)"
              [title]="favorites.isCategoryPinned(node.id) ? 'Retirer des favoris' : 'Ajouter ce rayon aux favoris'"
              (click)="favorites.toggleCategory(node.id)"
            >
              {{ favorites.isCategoryPinned(node.id) ? '★' : '☆' }}
            </button>
            @if (node.children) {
              <!-- Like an explorer, a folder lists what is filed in it; this shows the whole branch. -->
              <button type="button" class="cn-all" (click)="toggleAll(node.id)">
                {{ showsAll() ? 'Seulement ce rayon' : 'Tout afficher' }}
              </button>
            }
          } @else {
            <button type="button" class="cn-crumb" (click)="go({ kind: 'rayon', id: node.id })">
              {{ node.name }}
            </button>
          }
        }
      </nav>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .cn-path {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 4px 6px;
      font-size: 13px;
      margin: 0 0 10px;
    }

    .cn-crumb {
      border: none;
      background: none;
      padding: 2px 0;
      color: var(--brand-dark);
      font: inherit;
      cursor: pointer;
      text-decoration: underline;
    }

    .cn-sep {
      color: var(--ink-soft);
    }

    .cn-pin {
      border: none;
      background: none;
      font-size: 17px;
      line-height: 1;
      color: var(--ink-soft);
      cursor: pointer;
      padding: 0 2px;

      &.on {
        color: var(--amber);
      }
    }

    .cn-all {
      margin-left: auto;
      border: 1px solid var(--line);
      border-radius: 20px;
      background: var(--bg);
      color: inherit;
      font: inherit;
      font-size: 12px;
      padding: 4px 10px;
      cursor: pointer;
    }
  `,
})
export class CatalogNavigatorComponent {
  protected readonly favorites = inject(FavoritesStore);

  /** The whole rayon tree, depth-first — what CatalogApi.categories serves. */
  readonly categories = input.required<readonly CategoryNode[]>();
  readonly scope = input.required<CatalogScope>();

  readonly scopeChange = output<CatalogScope>();

  protected readonly featured = FEATURED;

  private readonly byId = computed(() => new Map(this.categories().map((node) => [node.id, node])));

  /** The open rayon and its ancestors, root first; empty outside a rayon. */
  protected readonly path = computed(() => {
    const scope = this.scope();
    const path: CategoryNode[] = [];
    for (let node = scope.kind === 'rayon' ? this.byId().get(scope.id) : undefined; node;
         node = node.parentId === null ? undefined : this.byId().get(node.parentId)) {
      path.unshift(node);
    }
    return path;
  });

  protected readonly showsAll = computed(() => {
    const scope = this.scope();
    return scope.kind === 'rayon' && !!scope.all;
  });

  protected go(scope: CatalogScope): void {
    this.scopeChange.emit(scope);
  }

  protected toggleAll(id: number): void {
    this.go({ kind: 'rayon', id, all: !this.showsAll() });
  }
}
