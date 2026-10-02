import { Component, inject, input, output, signal } from '@angular/core';
import { CatalogFolder, Product } from '../../core/models';
import { Cart } from '../../core/sale/cart';
import { stockLabel } from '../../core/sale/sale-unit';
import { FavoritesStore } from '../../core/store/favorites.store';
import { AriaryPipe } from '../pipes/ariary.pipe';
import { FolderTileComponent } from './folder-tile.component';
import { UnitPicked, UnitPickerComponent } from './unit-picker.component';

/** A unit tile tapped on a product sold in several units. */
export interface GridUnitPicked {
  readonly product: Product;
  readonly picked: UnitPicked;
}

/**
 * The till's grid as a file explorer: the rayons as folders first — the seller's favourites as
 * one more folder at the top of the shop — then the products, as tiles.
 *
 * <p>A product sold one way goes in on a tap; one sold in several units opens its unit buttons
 * under the tile instead. The count and the "−" sit beside the card, not in it: a button cannot
 * hold another, and a tap on "−" must never also count as a tap on the product.
 *
 * <p>What the grid shows and what a folder opens to are the screen's: it hands the folders and
 * products in, and hears which folder was opened and which product went in.
 */
@Component({
  selector: 'app-catalog-grid',
  standalone: true,
  imports: [AriaryPipe, FolderTileComponent, UnitPickerComponent],
  template: `
    <div class="prod-grid">
      @if (showFavorites() && favorites.any()) {
        <button type="button" class="cg-favorites" (click)="favoritesOpened.emit()">
          <span class="cg-star" aria-hidden="true">★</span>
          <span class="cg-favorites-name">Favoris</span>
        </button>
      }
      @for (folder of folders(); track folder.rayon.id) {
        <app-folder-tile [folder]="folder" (opened)="folderOpened.emit($event)" />
      }
      @for (product of products(); track product.id) {
        <div class="prod-item">
          <button
            type="button"
            class="prod-pin"
            [class.on]="favorites.isProductPinned(product.id)"
            [attr.aria-pressed]="favorites.isProductPinned(product.id)"
            [title]="favorites.isProductPinned(product.id) ? 'Retirer des favoris' : 'Ajouter aux favoris'"
            (click)="favorites.toggleProduct(product.id)"
          >
            {{ favorites.isProductPinned(product.id) ? '★' : '☆' }}
          </button>
          <button
            type="button"
            class="prod-card"
            [class.low]="product.lowStock"
            [class.picked]="cart().quantityOf(product) > 0"
            [class.short]="cart().isShort(product)"
            [disabled]="cart().remainingStock(product) <= 0"
            (click)="tap(product)"
          >
            <div class="pname">{{ product.name }}</div>
            <div class="pcat">{{ product.category?.name ?? 'Sans catégorie' }}</div>
            <div class="pprice">
              {{ product.price | ariary }}@if (product.unit) {<span class="muted"> / {{ product.unit }}</span>}
            </div>
            <div class="pstock">Disponible : {{ stockLabel(product, cart().remainingStock(product)) }}</div>
            @if (hasUnits(product)) {
              <div class="punits">Aussi : {{ otherUnits(product) }}</div>
            }
          </button>
          @if (hasUnits(product)) {
            <!-- Sold in several units: one tile still, and the units open under it. -->
            @if (unitsOpen() === product.id || cart().quantityOf(product) > 0) {
              <app-unit-picker
                [cart]="cart()"
                [product]="product"
                (picked)="unitPicked.emit({ product, picked: $event })"
              />
            }
          } @else if (cart().quantityOf(product) > 0) {
            <span class="prod-count" [class.short]="cart().isShort(product)" aria-label="Quantité dans le panier">
              {{ cart().countLabelOf(product) }}
            </span>
            <button
              type="button"
              class="prod-minus"
              [attr.aria-label]="'Retirer un ' + product.name"
              (click)="cart().change(product, -1)"
            >
              −
            </button>
          }
        </div>
      } @empty {
        @if (!folders().length && !pending()) {
          <div class="empty">{{ emptyMessage() }}</div>
        }
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .cg-favorites {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 4px;
      min-height: 120px;
      border: 1px solid var(--line);
      border-radius: 9px;
      background: var(--bg);
      color: inherit;
      font: inherit;
      cursor: pointer;

      &:hover {
        border-color: var(--brand);
      }
    }

    .cg-star {
      font-size: 30px;
      color: var(--amber);
    }

    .cg-favorites-name {
      font-weight: 600;
      font-size: 13px;
    }
  `,
})
export class CatalogGridComponent {
  protected readonly favorites = inject(FavoritesStore);

  readonly folders = input<readonly CatalogFolder[]>([]);
  readonly products = input.required<readonly Product[]>();
  readonly cart = input.required<Cart>();
  /** At the top of the shop: the favourites as a folder of their own. */
  readonly showFavorites = input(false);
  /** An answer is on its way: no "nothing found" meanwhile. */
  readonly pending = input(false);
  readonly emptyMessage = input('Aucun produit.');

  readonly folderOpened = output<CatalogFolder>();
  readonly favoritesOpened = output<void>();
  /** A product tile was tapped: it went in, or its unit buttons opened. */
  readonly tapped = output<Product>();
  readonly unitPicked = output<GridUnitPicked>();

  /** The product whose unit buttons are open, before anything of it is in the basket. */
  protected readonly unitsOpen = signal<number | null>(null);
  protected readonly stockLabel = stockLabel;

  /** A product sold one way goes in; one sold in several units opens its unit buttons instead. */
  protected tap(product: Product): void {
    if (this.hasUnits(product)) {
      this.unitsOpen.update((open) => (open === product.id ? null : product.id));
    } else {
      this.cart().add(product);
    }
    this.tapped.emit(product);
  }

  protected hasUnits(product: Product): boolean {
    return (product.packagings?.length ?? 0) > 0;
  }

  /** "kg, sac 50 kg": what else the tile sells in, so the seller knows a tap offers a choice. */
  protected otherUnits(product: Product): string {
    return (product.packagings ?? []).map((packaging) => packaging.label).join(', ');
  }
}
