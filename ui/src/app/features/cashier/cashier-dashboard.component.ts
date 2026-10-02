import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardApi } from '../../core/api/dashboard.api';
import { apiResource } from '../../core/api/api-resource';
import { CashierDashboard } from '../../core/models';
import { reloadOnOrderChange } from '../../core/realtime/reload-on';
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
  remittancesToConfirm: 0,
  remittancesToConfirmAmount: 0,
  latestInvoices: [],
};

@Component({
  selector: 'app-cashier-dashboard',
  standalone: true,
  imports: [RouterLink, KpiCardComponent, InvoiceTableComponent, AriaryPipe],
  styles: `
    .to-confirm {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 8px;
      margin-top: 16px;
      border-left: 4px solid var(--blue);
      color: inherit;
      text-decoration: none;
    }
  `,
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

    <!-- Cash waiting at the desk is what a cashier must act on: shown only when there is some. -->
    @if (data().remittancesToConfirm) {
      <a class="card to-confirm" routerLink="/caisse/versements">
        <strong>
          {{ data().remittancesToConfirm }} versement(s) du dépôt à confirmer :
          {{ data().remittancesToConfirmAmount | ariary }}
        </strong>
        <span class="muted">Comptez l'argent reçu puis confirmez la réception ›</span>
      </a>
    }

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

  constructor() {
    // An order handed over at the depot changes what is still to be collected.
    reloadOnOrderChange(this.dashboard);
  }
}
