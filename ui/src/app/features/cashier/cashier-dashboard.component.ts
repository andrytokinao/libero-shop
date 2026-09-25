import { Component, inject } from '@angular/core';
import { DashboardApi } from '../../core/api/dashboard.api';
import { apiResource } from '../../core/api/api-resource';
import { CashierDashboard } from '../../core/models';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

const EMPTY: CashierDashboard = {
  revenueToday: 0,
  paidSalesToday: 0,
  invoicesToday: 0,
  unpaidInvoicesToday: 0,
  averageBasket: 0,
  outstandingToday: 0,
  lowStockCount: 0,
  latestInvoices: [],
};

@Component({
  selector: 'app-cashier-dashboard',
  standalone: true,
  imports: [KpiCardComponent, InvoiceTableComponent, AriaryPipe],
  template: `
    <div class="grid g4">
      <app-kpi-card
        label="Mon chiffre d'affaires aujourd'hui"
        [value]="data().revenueToday | ariary"
        [hint]="data().paidSalesToday + ' vente(s) encaissée(s)'"
      />
      <app-kpi-card
        label="Factures émises aujourd'hui"
        [value]="data().invoicesToday"
        [hint]="'dont ' + data().unpaidInvoicesToday + ' non payée(s)'"
      />
      <app-kpi-card
        label="Panier moyen"
        [value]="data().averageBasket | ariary"
        [hint]="'reste à encaisser : ' + (data().outstandingToday | ariary)"
      />
      <app-kpi-card
        label="Produits en alerte stock"
        [value]="data().lowStockCount"
        hint="stock inférieur à 10"
      />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Mes dernières factures</h2>
      <app-invoice-table
        [invoices]="data().latestInvoices"
        [showSeller]="false"
        [showDate]="true"
        emptyMessage="Vous n'avez émis aucune facture."
      />
    </div>
  `,
})
export class CashierDashboardComponent {
  private readonly api = inject(DashboardApi);
  private readonly dashboard = apiResource(EMPTY, () => this.api.cashier());
  protected readonly data = this.dashboard.value;
}
