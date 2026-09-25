import { Component, inject } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { DashboardApi } from '../../core/api/dashboard.api';
import { CategoryStock, Product, StockDashboard } from '../../core/models';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { StockTableComponent } from '../../shared/components/stock-table.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

const EMPTY: StockDashboard = {
  stockValue: 0,
  referenceCount: 0,
  unitsInStock: 0,
  lowStockCount: 0,
  suppliesLastWeek: 0,
  unitsSuppliedLastWeek: 0,
  deliveredInvoices: 0,
  toRestock: [],
};

@Component({
  selector: 'app-global-stock',
  standalone: true,
  imports: [KpiCardComponent, StockTableComponent, AriaryPipe],
  template: `
    <div class="grid g4">
      <app-kpi-card label="Valeur du stock" [value]="summary().stockValue | ariary" />
      <app-kpi-card label="Références" [value]="summary().referenceCount" />
      <app-kpi-card label="Unités en stock" [value]="summary().unitsInStock" />
      <app-kpi-card
        label="Références en alerte"
        [value]="summary().lowStockCount"
        hint="stock inférieur à 10"
      />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Valeur du stock par catégorie</h2>
      @if (byCategory().length) {
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
                <td class="num">{{ row.sharePercent }} %</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty">Aucune catégorie enregistrée.</div>
      }
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Stock global <small>toutes catégories</small></h2>
      <app-stock-table [products]="products()" />
    </div>
  `,
})
export class GlobalStockComponent {
  private readonly dashboardApi = inject(DashboardApi);
  private readonly catalogApi = inject(CatalogApi);

  // The per-category split and its percentages are computed server-side, so this screen
  // and the depot manager's read exactly the same figures.
  private readonly summaryResource = apiResource(EMPTY, () => this.dashboardApi.stock());
  private readonly categoryResource = apiResource<CategoryStock[]>([], () =>
    this.catalogApi.stockByCategory(),
  );
  private readonly productResource = apiResource<Product[]>([], () => this.catalogApi.products());

  protected readonly summary = this.summaryResource.value;
  protected readonly byCategory = this.categoryResource.value;
  protected readonly products = this.productResource.value;
}
