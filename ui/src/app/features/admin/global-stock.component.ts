import { Component, computed, inject } from '@angular/core';
import { Category } from '../../core/models';
import { ShopStore } from '../../core/services/shop-store.service';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { StockTableComponent } from '../../shared/components/stock-table.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

interface CategoryStock {
  category: Category;
  references: number;
  units: number;
  value: number;
}

@Component({
  selector: 'app-global-stock',
  standalone: true,
  imports: [KpiCardComponent, StockTableComponent, AriaryPipe],
  template: `
    <div class="grid g4">
      <app-kpi-card label="Valeur du stock" [value]="store.stockValue() | ariary" />
      <app-kpi-card label="Références" [value]="store.products().length" />
      <app-kpi-card label="Unités en stock" [value]="totalUnits()" />
      <app-kpi-card
        label="Références en alerte"
        [value]="store.lowStockProducts().length"
        hint="stock inférieur à 10"
      />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Valeur du stock par catégorie</h2>
      <table>
        <thead>
          <tr>
            <th>Catégorie</th>
            <th class="num">Références</th>
            <th class="num">Unités</th>
            <th class="num">Valeur</th>
            <th class="num">Part</th>
          </tr>
        </thead>
        <tbody>
          @for (row of byCategory(); track row.category.id) {
            <tr>
              <td>{{ row.category.name }}</td>
              <td class="num">{{ row.references }}</td>
              <td class="num">{{ row.units }}</td>
              <td class="num">{{ row.value | ariary }}</td>
              <td class="num">{{ share(row) }} %</td>
            </tr>
          }
        </tbody>
      </table>
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Stock global <small>toutes catégories</small></h2>
      <app-stock-table [products]="store.products()" />
    </div>
  `,
})
export class GlobalStockComponent {
  protected readonly store = inject(ShopStore);

  protected readonly totalUnits = computed(() =>
    this.store.products().reduce((total, p) => total + p.stockQuantity, 0),
  );

  protected readonly byCategory = computed<CategoryStock[]>(() =>
    this.store
      .categories()
      .map((category) => {
        const products = this.store.products().filter((p) => p.category?.id === category.id);
        return {
          category,
          references: products.length,
          units: products.reduce((total, p) => total + p.stockQuantity, 0),
          value: products.reduce((total, p) => total + p.price * p.stockQuantity, 0),
        };
      })
      .sort((a, b) => b.value - a.value),
  );

  protected share(row: CategoryStock): number {
    const total = this.store.stockValue();
    return total ? Math.round((row.value / total) * 100) : 0;
  }
}
