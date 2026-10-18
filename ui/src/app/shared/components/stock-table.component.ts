import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProductDialogRequest } from '../../core/catalog/product-rights';
import { Product } from '../../core/models';
import { formatQuantity, stockLabel } from '../../core/sale/sale-unit';
import { LoadState } from '../../core/api/load-state';
import { AriaryPipe } from '../pipes/ariary.pipe';
import { LoadStateComponent, showsEmpty } from './load-state.component';
import { ProductMenuComponent } from './product-menu.component';
import { StockStatusBadgeComponent } from './status-badges.component';
import { LongPressMenuDirective } from '../directives/long-press-menu.directive';

@Component({
  selector: 'app-stock-table',
  standalone: true,
  imports: [
    AriaryPipe,
    LoadStateComponent,
    LongPressMenuDirective,
    ProductMenuComponent,
    StockStatusBadgeComponent,
  ],
  template: `
    <app-load-state [of]="state" what="le stock" />
    @if (products.length) {
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Produit</th>
              <th>Catégorie</th>
              <th>Code-barres</th>
              <th class="num">Stock</th>
              <th>Unité</th>
              <th class="num">Prix unitaire</th>
              <th class="num">Valeur stock</th>
              <th></th>
              @if (manage) {
                <th></th>
              }
            </tr>
          </thead>
          <tbody>
            @for (product of products; track product.id) {
              <tr appLongPressMenu>
                <td>
                  <!-- Touching the name opens the product at a glance, its actions as buttons. -->
                  @if (manage) {
                    <button class="st-name" type="button" (click)="action.emit({ action: 'overview', product })">
                      {{ product.name }}
                    </button>
                  } @else {
                    {{ product.name }}
                  }
                </td>
                <td class="muted">{{ product.category?.name ?? '—' }}</td>
                <td class="muted">{{ product.barcode ?? '—' }}</td>
                <td class="num">
                  {{ formatQuantity(product.stockQuantity) }}
                  <!-- Sold in several units: also in the one that reads best, "≈ 9,96 sac". -->
                  @if (product.packagings?.length) {
                    <div class="muted" style="font-size:11px;">≈ {{ stockLabel(product) }}</div>
                  }
                </td>
                <td class="muted">{{ product.unit ?? '—' }}</td>
                <td class="num">{{ product.price | ariary }}</td>
                <td class="num">{{ product.stockValue | ariary }}</td>
                <td>
                  <app-stock-status-badge
                    [quantity]="product.stockQuantity"
                    [lowStock]="product.lowStock"
                    [retired]="!!product.retired"
                  />
                </td>
                @if (manage) {
                  <td class="num">
                    <app-product-menu
                      [product]="product"
                      [openNow]="product.id === openMenuOf"
                      (action)="action.emit($event)"
                    />
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else if (showsEmpty(state)) {
      <div class="empty">{{ emptyMessage }}</div>
    }
  `,
  styles: `
    /* The name, a link to the product at a glance: looks like text, underlined on hover. */
    .st-name {
      border: 0;
      background: none;
      padding: 0;
      font: inherit;
      color: inherit;
      text-align: left;
      cursor: pointer;

      &:hover,
      &:focus-visible {
        color: var(--brand-ink);
        text-decoration: underline;
      }
    }
  `,
})
export class StockTableComponent {
  @Input({ required: true }) products: readonly Product[] = [];
  /** The list's loading state: "Chargement…" or the failure in place of the empty message. */
  @Input() state?: LoadState;
  protected readonly showsEmpty = showsEmpty;
  @Input() emptyMessage = 'Aucun produit à afficher.';
  /**
   * Offers a "⋮" menu per row — add stock, edit, correct, history, whichever the account may do.
   * Off by default: the table is also the read-only stock of the dashboards, and only the
   * screens where the catalogue is managed turn it on.
   */
  @Input() manage = false;
  /** The product whose menu opens at once: the one a scanned code found. */
  @Input() openMenuOf: number | null = null;
  /** What a row's menu was asked; the screen hands it to `app-product-dialogs`. */
  @Output() readonly action = new EventEmitter<ProductDialogRequest>();

  protected readonly formatQuantity = formatQuantity;
  protected readonly stockLabel = stockLabel;
}
