import { Component, Input } from '@angular/core';
import { Product } from '../../core/models';
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
            <th class="num">Prix unitaire</th>
            <th class="num">Valeur stock</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          @for (product of products; track product.id) {
            <tr>
              <td>{{ product.name }}</td>
              <td class="muted">{{ product.category?.name ?? '—' }}</td>
              <td class="muted">{{ product.barcode ?? '—' }}</td>
              <td class="num">{{ product.stockQuantity }}</td>
              <td class="num">{{ product.price | ariary }}</td>
              <td class="num">{{ product.stockValue | ariary }}</td>
              <td>
                <app-stock-status-badge
                  [quantity]="product.stockQuantity"
                  [lowStock]="product.lowStock"
                />
              </td>
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
}
