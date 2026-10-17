import { Component, input, output } from '@angular/core';
import { ProductDialogRequest } from '../../core/catalog/product-rights';
import { Product } from '../../core/models';
import { formatAmount, stockLabel } from '../../core/sale/sale-unit';
import { AriaryPipe } from '../pipes/ariary.pipe';
import { ProductMenuComponent } from './product-menu.component';
import { StockStatusBadgeComponent } from './status-badges.component';
import { LongPressMenuDirective } from '../directives/long-press-menu.directive';

/**
 * One product as a card: what it is, what it sells for, what is left of it — and its "⋮" menu
 * for the accounts allowed to act on it.
 *
 * <p>Reusable wherever products are managed rather than sold: the till's tiles are buttons that
 * put an article in a basket, and a button cannot hold a menu. The card knows nothing of the
 * dialogs; it passes on what its menu was asked, like a row of `app-stock-table`.
 */
@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [LongPressMenuDirective, AriaryPipe, ProductMenuComponent, StockStatusBadgeComponent],
  template: `
    <article class="pc" appLongPressMenu [class.low]="product().lowStock">
      <header class="pc-head">
        <div class="pc-title">
          @if (manage()) {
            <!-- Touching the name opens the product at a glance, its actions as buttons. -->
            <button class="pc-name pc-link" type="button" (click)="action.emit({ action: 'overview', product: product() })">
              {{ product().name }}
            </button>
          } @else {
            <div class="pc-name">{{ product().name }}</div>
          }
          <div class="pc-cat">{{ product().category?.name ?? 'Sans catégorie' }}</div>
        </div>
        @if (manage()) {
          <app-product-menu [product]="product()" [openNow]="openMenu()" (action)="action.emit($event)" />
        }
      </header>
      <div class="pc-price">
        {{ product().price | ariary }}@if (product().unit) {<span class="muted"> / {{ product().unit }}</span>}
      </div>
      <div class="pc-stock">
        <span>Stock : {{ formatAmount(product().stockQuantity, product().unit) }}</span>
        <app-stock-status-badge
          [quantity]="product().stockQuantity"
          [lowStock]="product().lowStock"
          [retired]="!!product().retired"
        />
      </div>
      @if (product().packagings?.length) {
        <!-- Sold in several units: the stock again in the one that reads best. -->
        <div class="pc-more">≈ {{ stockLabel(product()) }} · aussi : {{ otherUnits() }}</div>
      }
      @if (product().barcode) {
        <div class="pc-more">Code : {{ product().barcode }}</div>
      }
    </article>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }

    .pc {
      height: 100%;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: var(--panel);
      padding: 12px;
    }

    .pc-head {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 6px;
    }

    .pc-title {
      min-width: 0;
    }

    .pc-name {
      font-weight: 600;
      font-size: 13.5px;
      overflow-wrap: anywhere;
    }

/* The name, a link to the product at a glance: looks like text, underlined on hover. */
    .pc-link {
      border: 0;
      background: none;
      padding: 0;
      font-family: inherit;
      color: inherit;
      text-align: left;
      cursor: pointer;

      &:hover,
      &:focus-visible {
        color: var(--brand);
        text-decoration: underline;
      }
    }

    .pc-cat {
      font-size: 11px;
      color: var(--ink-soft);
      margin-top: 2px;
    }

    .pc-price {
      color: var(--brand-dark);
      font-weight: 700;
      font-size: 14px;
      margin-top: 8px;
    }

    .pc-stock {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      flex-wrap: wrap;
      font-size: 12.5px;
      margin-top: 6px;
    }

    .pc.low .pc-stock > span {
      color: var(--red);
      font-weight: 600;
    }

    .pc-more {
      font-size: 11.5px;
      color: var(--ink-soft);
      margin-top: 4px;
      overflow-wrap: anywhere;
    }
  `,
})
export class ProductCardComponent {
  readonly product = input.required<Product>();
  /** Shows the "⋮" menu; what it offers still depends on the account. Off for a read-only list. */
  readonly manage = input(false);
  /** Its menu opened at once: the product a scanned code found. */
  readonly openMenu = input(false);
  readonly action = output<ProductDialogRequest>();

  protected readonly formatAmount = formatAmount;
  protected readonly stockLabel = stockLabel;

  protected otherUnits(): string {
    return (this.product().packagings ?? []).map((packaging) => packaging.label).join(', ');
  }
}
