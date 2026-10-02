import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Product } from '../../core/models';
import { formatQuantity, stockLabel } from '../../core/sale/sale-unit';
import { AriaryPipe } from '../pipes/ariary.pipe';
import { StockStatusBadgeComponent } from './status-badges.component';

@Component({
  selector: 'app-stock-table',
  standalone: true,
  imports: [AriaryPipe, StockStatusBadgeComponent],
  template: `
    @if (products.length) {
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
            @if (manageUnits) {
              <th></th>
            }
          </tr>
        </thead>
        <tbody>
          @for (product of products; track product.id) {
            <tr>
              <td>{{ product.name }}</td>
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
                />
              </td>
              @if (manageUnits) {
                <td>
                  <button
                    class="btn small ghost"
                    type="button"
                    title="Unités de vente : kg, carton, sac…"
                    (click)="unitsRequested.emit(product)"
                  >
                    Unités
                  </button>
                </td>
              }
            </tr>
          }
        </tbody>
      </table>
    } @else {
      <div class="empty">{{ emptyMessage }}</div>
    }
  `,
})
export class StockTableComponent {
  @Input({ required: true }) products: readonly Product[] = [];
  @Input() emptyMessage = 'Aucun produit à afficher.';
  /**
   * Offers a "Unités" button per row. Off by default: the table is also the read-only stock of
   * the dashboards, and only the screen of the roles that own the catalogue turns it on.
   */
  @Input() manageUnits = false;
  @Output() readonly unitsRequested = new EventEmitter<Product>();

  protected readonly formatQuantity = formatQuantity;
  protected readonly stockLabel = stockLabel;
}
