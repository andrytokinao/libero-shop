import { Component, inject } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { DashboardApi } from '../../core/api/dashboard.api';
import { StockDashboard } from '../../core/models';
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
  selector: 'app-manager-dashboard',
  standalone: true,
  imports: [KpiCardComponent, StockTableComponent, AriaryPipe],
  template: `
    <div class="grid g4">
      <app-kpi-card
        label="Valeur totale du stock"
        [value]="data().stockValue | ariary"
        [hint]="data().referenceCount + ' références, ' + data().unitsInStock + ' unités'"
      />
      <app-kpi-card
        label="Références en alerte"
        [value]="data().lowStockCount"
        hint="stock inférieur à 10"
      />
      <app-kpi-card
        label="Approvisionnements (7 j)"
        [value]="data().suppliesLastWeek"
        [hint]="data().unitsSuppliedLastWeek + ' unités reçues'"
      />
      <app-kpi-card label="Commandes livrées" [value]="data().deliveredInvoices" />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Produits à réapprovisionner en priorité
        <small>triés du stock le plus faible au plus élevé</small>
      </h2>
      <app-stock-table
        [products]="data().toRestock"
        emptyMessage="Aucun produit en alerte — le stock est sain."
      />
    </div>
  `,
})
export class ManagerDashboardComponent {
  private readonly api = inject(DashboardApi);
  private readonly resource = apiResource(EMPTY, () => this.api.stock());
  protected readonly data = this.resource.value;
}
