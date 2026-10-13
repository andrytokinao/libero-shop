import { Component, ElementRef, computed, inject, input, output, signal } from '@angular/core';
import { CatalogFolder, Product } from '../../core/models';
import { Cart } from '../../core/sale/cart';
import { stockLabel } from '../../core/sale/sale-unit';
import { FavoritesStore } from '../../core/store/favorites.store';
import { AriaryPipe } from '../pipes/ariary.pipe';
import { GridUnitPicked } from './catalog-grid.component';
import { UnitPickerComponent } from './unit-picker.component';

/** What Enter did to the highlighted line: how many went in — fewer than asked on a short shelf. */
export interface ListPicked {
  readonly product: Product;
  readonly added: number;
}

/**
 * The sale screens' products as lines rather than tiles: a name read in full, its rayon and what
 * the shelf holds under it, the price in a column, and "−  2  +" at the end of the line — three
 * times as many articles on a screen as {@link CatalogGridComponent} shows, for a catalogue found
 * by its words more than by its pictures.
 *
 * <p>Made for the keyboard too: the screen's search field keeps the focus and hands the arrows
 * here ({@link move}), Enter then adds the highlighted line ({@link pickActive}) — type "riz", ↓,
 * Enter, without leaving the field a barcode scanner types into.
 *
 * <p>The rayons are chips above the lines: a folder has nothing to say on a line of its own. The
 * same inputs and outputs as the grid, so a screen swaps one for the other.
 */
@Component({
  selector: 'app-catalog-list',
  standalone: true,
  imports: [AriaryPipe, UnitPickerComponent],
  template: `
    @if (folders().length || (showFavorites() && favorites.any())) {
      <div class="cl-folders">
        @if (showFavorites() && favorites.any()) {
          <button type="button" class="cl-folder" (click)="favoritesOpened.emit()">
            <span class="cl-star" aria-hidden="true">★</span> Favoris
          </button>
        }
        @for (folder of folders(); track folder.rayon.id) {
          <button
            type="button"
            class="cl-folder"
            [attr.aria-label]="'Ouvrir le rayon ' + folder.rayon.name"
            (click)="folderOpened.emit(folder)"
          >
            {{ folder.rayon.name }} <span class="cl-holds">{{ folder.products }}</span>
          </button>
        }
      </div>
    }
    @if (keyboardHint() && products().length) {
      <div class="cl-hint">↑ ↓ pour parcourir · Entrée pour ajouter</div>
    }
    <div class="cl-rows">
      @for (product of products(); track product.id) {
        <div
          class="cl-row"
          [attr.data-product]="product.id"
          [class.active]="active()?.id === product.id"
          [class.low]="product.lowStock"
          [class.picked]="cart().quantityOf(product) > 0"
          [class.short]="cart().isShort(product)"
        >
          <button
            type="button"
            class="cl-pin"
            [class.on]="favorites.isProductPinned(product.id)"
            [attr.aria-pressed]="favorites.isProductPinned(product.id)"
            [title]="favorites.isProductPinned(product.id) ? 'Retirer des favoris' : 'Ajouter aux favoris'"
            (click)="favorites.toggleProduct(product.id)"
          >
            {{ favorites.isProductPinned(product.id) ? '★' : '☆' }}
          </button>
          <button
            type="button"
            class="cl-main"
            [disabled]="cart().remainingStock(product) <= 0"
            (click)="tap(product)"
          >
            <span class="cl-text">
              <span class="cl-name">{{ product.name }}</span>
              <span class="cl-sub">
                {{ product.category?.name ?? 'Sans catégorie' }} ·
                <span class="cl-stock">
                  @if (cart().remainingStock(product) > 0) {
                    Disponible : {{ stockLabel(product, cart().remainingStock(product)) }}
                  } @else {
                    Épuisé
                  }
                </span>
                @if (hasUnits(product)) {
                  · Aussi : {{ otherUnits(product) }}
                }
              </span>
            </span>
            <span class="cl-price">
              {{ product.price | ariary }}@if (product.unit) {<span class="muted"> / {{ product.unit }}</span>}
            </span>
          </button>
          <span class="cl-qty">
            @if (hasUnits(product)) {
              <!-- Sold in several units: each has its own "−" in the buttons under the line. -->
              @if (cart().quantityOf(product) > 0) {
                <span class="cl-count" aria-label="Quantité dans le panier">{{ cart().countLabelOf(product) }}</span>
              } @else {
                <button
                  type="button"
                  class="cl-step"
                  [attr.aria-label]="'Choisir une unité de ' + product.name"
                  [disabled]="cart().remainingStock(product) <= 0"
                  (click)="tap(product)"
                >
                  +
                </button>
              }
            } @else {
              @if (cart().quantityOf(product) > 0) {
                <button
                  type="button"
                  class="cl-step"
                  [attr.aria-label]="'Retirer un ' + product.name"
                  (click)="cart().change(product, -1)"
                >
                  −
                </button>
                <span class="cl-count" aria-label="Quantité dans le panier">{{ cart().countLabelOf(product) }}</span>
              }
              <button
                type="button"
                class="cl-step"
                [attr.aria-label]="'Ajouter un ' + product.name"
                [disabled]="cart().remainingStock(product) <= 0"
                (click)="tap(product)"
              >
                +
              </button>
            }
          </span>
          @if (hasUnits(product) && (unitsOpen() === product.id || cart().quantityOf(product) > 0)) {
            <app-unit-picker
              class="cl-units"
              [cart]="cart()"
              [product]="product"
              (picked)="unitPicked.emit({ product, picked: $event })"
            />
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

    .cl-folders {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-bottom: 10px;
    }

    .cl-folder {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border: 1px solid var(--line);
      border-radius: 20px;
      background: var(--bg);
      color: inherit;
      font: inherit;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;

      &:hover {
        border-color: var(--brand);
      }
    }

    .cl-star {
      color: var(--amber);
    }

    .cl-holds {
      font-size: 11.5px;
      font-weight: 400;
      color: var(--ink-soft);
    }

    .cl-hint {
      margin-bottom: 6px;
      font-size: 11.5px;
      color: var(--ink-soft);
      text-align: right;
    }

    .cl-rows {
      border: 1px solid var(--line);
      border-radius: 9px;
      overflow: hidden;

      &:empty {
        display: none;
      }
    }

    /* The star, the product, what the basket holds of it; the unit buttons on a line of their own. */
    .cl-row {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 0 4px;
      padding: 2px 8px 2px 4px;
      background: var(--panel);

      & + .cl-row {
        border-top: 1px solid var(--line);
      }

      &.picked {
        background: var(--brand-soft);
      }

      &.short {
        background: var(--red-soft);
      }

      /* The line the arrows are on: inside the row, so the rounded frame does not clip it. */
      &.active {
        box-shadow: inset 3px 0 0 var(--brand), inset 0 0 0 1px var(--brand);
      }
    }

    .cl-pin {
      width: 28px;
      height: 28px;
      border: none;
      border-radius: 50%;
      background: none;
      color: var(--ink-soft);
      font-size: 16px;
      line-height: 1;
      cursor: pointer;

      &.on {
        color: var(--amber);
      }
    }

    .cl-main {
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 0;
      padding: 8px 4px;
      border: none;
      background: none;
      color: inherit;
      font: inherit;
      text-align: left;
      cursor: pointer;

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }

    .cl-text {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 1px;
    }

    .cl-name {
      font-weight: 600;
      font-size: 13.5px;
    }

    .cl-sub {
      font-size: 11.5px;
      color: var(--ink-soft);
    }

    .cl-row.low .cl-stock {
      color: var(--red);
      font-weight: 600;
    }

    .cl-price {
      flex: none;
      color: var(--brand-dark);
      font-weight: 700;
      font-size: 13.5px;
      white-space: nowrap;
    }

    .cl-qty {
      display: inline-flex;
      align-items: center;
      justify-content: flex-end;
      gap: 6px;
      min-width: 34px;
    }

    .cl-step {
      width: 32px;
      height: 32px;
      border: 1px solid var(--brand);
      border-radius: 50%;
      background: var(--panel);
      color: var(--brand-dark);
      font: inherit;
      font-size: 18px;
      font-weight: 700;
      line-height: 1;
      cursor: pointer;

      &:hover:not(:disabled) {
        background: var(--brand-soft);
      }

      &:disabled {
        opacity: 0.4;
        cursor: not-allowed;
      }
    }

    .cl-count {
      min-width: 24px;
      padding: 2px 7px;
      border-radius: 12px;
      background: var(--brand);
      color: var(--on-brand);
      font-weight: 700;
      font-size: 13px;
      text-align: center;
      white-space: nowrap;
    }

    .cl-row.short .cl-count {
      background: var(--red);
    }

    .cl-units {
      grid-column: 2 / -1;
      padding: 0 4px 8px;
    }

    /* One column (the sale screens' breakpoint): no keyboard to hint at. */
    @media (max-width: 980px) {
      .cl-hint {
        display: none;
      }
    }

    /* A phone: the name keeps the width, the price goes under it. */
    @media (max-width: 560px) {
      .cl-main {
        flex-direction: column;
        align-items: flex-start;
        gap: 2px;
        padding: 10px 4px;
      }

      .cl-step {
        width: 36px;
        height: 36px;
      }
    }
  `,
})
export class CatalogListComponent {
  protected readonly favorites = inject(FavoritesStore);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  readonly folders = input<readonly CatalogFolder[]>([]);
  readonly products = input.required<readonly Product[]>();
  readonly cart = input.required<Cart>();
  /** At the top of the shop: the favourites as a chip of their own. */
  readonly showFavorites = input(false);
  /** An answer is on its way: no "nothing found" meanwhile. */
  readonly pending = input(false);
  readonly emptyMessage = input('Aucun produit.');
  /** The screen hands the arrows and Enter of its search field here: say so above the lines. */
  readonly keyboardHint = input(false);

  readonly folderOpened = output<CatalogFolder>();
  readonly favoritesOpened = output<void>();
  /** A line was tapped: it went in, or its unit buttons opened. */
  readonly tapped = output<Product>();
  readonly unitPicked = output<GridUnitPicked>();

  /** The product whose unit buttons are open, before anything of it is in the basket. */
  protected readonly unitsOpen = signal<number | null>(null);
  protected readonly stockLabel = stockLabel;

  private readonly activeId = signal<number | null>(null);
  /** The line the arrows are on — none until an arrow is pressed, or once it left the list. */
  readonly active = computed(() => {
    const id = this.activeId();
    return id === null ? null : (this.products().find((product) => product.id === id) ?? null);
  });

  /** ↓ and ↑ from the search field: the first line (or the last) to start with, then one line at a time. */
  move(delta: 1 | -1): void {
    const products = this.products();
    if (!products.length) {
      return;
    }
    const active = this.active();
    const from = active ? products.indexOf(active) : delta > 0 ? -1 : products.length;
    const next = products[Math.max(0, Math.min(products.length - 1, from + delta))];
    this.activeId.set(next.id);
    // The row is already on screen, only its highlight changes: no render to wait for.
    this.host.nativeElement
      .querySelector(`[data-product="${next.id}"]`)
      ?.scrollIntoView({ block: 'nearest' });
  }

  /**
   * The words in the field changed: the highlight was on a line of the list they are replacing.
   * A scanner types its code and Enter faster than the search answers — without this, its Enter
   * would add the line highlighted before the scan.
   */
  clearActive(): void {
    this.activeId.set(null);
  }

  /**
   * Enter on the highlighted line: adds `quantity` of it, in the unit its last line is in — a
   * keyboard has no unit buttons to open first; they show under the line once it is in.
   *
   * @returns null when no line is highlighted — Enter is then the search field's own
   */
  pickActive(quantity = 1): ListPicked | null {
    const product = this.active();
    if (!product) {
      return null;
    }
    const added = this.cart().add(product, quantity);
    this.tapped.emit(product);
    return { product, added };
  }

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

  /** "kg, sac 50 kg": what else the line sells in, so the seller knows a tap offers a choice. */
  protected otherUnits(product: Product): string {
    return (product.packagings ?? []).map((packaging) => packaging.label).join(', ');
  }
}
