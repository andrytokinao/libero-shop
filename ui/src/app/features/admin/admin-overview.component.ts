import { Component, inject } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { DashboardApi } from '../../core/api/dashboard.api';
import { AdminDashboard } from '../../core/models';
import { reloadOnOrderChange, reloadOnStockChange } from '../../core/realtime/reload-on';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { RevenueBarsComponent } from '../../shared/components/revenue-bars.component';
import { StockTableComponent } from '../../shared/components/stock-table.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

const EMPTY: AdminDashboard = {
  revenueToday: 0,
  stockValue: 0,
  referenceCount: 0,
  unpaidInvoices: 0,
  unpaidAmount: 0,
  pendingDeliveries: 0,
  depotCashInHand: 0,
  depotCashInHandCount: 0,
  pendingRemittanceAmount: 0,
  pendingRemittanceCount: 0,
  confirmedRemittanceAmountToday: 0,
  revenueBySeller: [],
  lowStockProducts: [],
};

@Component({
  selector: 'app-admin-overview',
  standalone: true,
  imports: [KpiCardComponent, RevenueBarsComponent, StockTableComponent, AriaryPipe],
  template: `
    <div class="grid g4">
      <app-kpi-card
        label="Chiffre d'affaires du jour"
        [value]="data().revenueToday | ariary"
        hint="toutes caisses confondues"
      />
      <app-kpi-card
        label="Valeur du stock"
        [value]="data().stockValue | ariary"
        [hint]="data().referenceCount + ' références'"
      />
      <app-kpi-card
        label="Factures non payées"
        [value]="data().unpaidInvoices"
        [hint]="data().unpaidAmount | ariary"
      />
      <app-kpi-card label="Commandes en attente de remise" [value]="data().pendingDeliveries" />
    </div>

    <div class="grid g2" style="margin-top:16px;">
      <div class="card">
        <h2>
          Chiffre d'affaires par vendeur
          <small>mis à jour à chaque vente encaissée</small>
        </h2>
        <app-revenue-bars [data]="data().revenueBySeller" />
      </div>
      <div class="card">
        <h2>Produits en alerte stock</h2>
        <app-stock-table
          [products]="data().lowStockProducts"
          emptyMessage="Aucun produit en alerte."
        />
      </div>
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Suivi des espèces encaissées au dépôt
        <small>contrôle de la remise à la caisse</small>
      </h2>
      <div class="grid g3">
        <app-kpi-card
          [flat]="true"
          label="Encore en main des agents"
          [value]="data().depotCashInHand | ariary"
          [hint]="data().depotCashInHandCount + ' encaissement(s)'"
        />
        <app-kpi-card
          [flat]="true"
          label="Versements en attente de confirmation"
          [value]="data().pendingRemittanceAmount | ariary"
          [hint]="data().pendingRemittanceCount + ' bordereau(x)'"
        />
        <app-kpi-card
          [flat]="true"
          label="Versements confirmés aujourd'hui"
          [value]="data().confirmedRemittanceAmountToday | ariary"
        />
      </div>
    </div>
  `,
})
export class AdminOverviewComponent {
  private readonly api = inject(DashboardApi);
  private readonly resource = apiResource(EMPTY, () => this.api.admin());
  protected readonly data = this.resource.value;

  constructor() {
    // The whole shop at a glance: its takings move with the orders, its value with the stock.
    reloadOnOrderChange(this.resource);
    reloadOnStockChange(this.resource);
  }
}
