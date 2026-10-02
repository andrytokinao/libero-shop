import { Component, inject } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { DashboardApi } from '../../core/api/dashboard.api';
import { PAYMENT_METHOD_LABELS, PaymentStatus, RevenueReport } from '../../core/models';
import { reloadOnOrderChange } from '../../core/realtime/reload-on';
import { InvoiceStore } from '../../core/store/invoice.store';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { RevenueBarsComponent } from '../../shared/components/revenue-bars.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

const EMPTY: RevenueReport = {
  totalToday: 0,
  averageBasket: 0,
  topSeller: null,
  bySeller: [],
  byPaymentMethod: [],
};

@Component({
  selector: 'app-revenue',
  standalone: true,
  imports: [KpiCardComponent, RevenueBarsComponent, InvoiceTableComponent, AriaryPipe],
  template: `
    <div class="grid g3">
      <app-kpi-card
        label="Chiffre d'affaires du jour"
        [value]="report().totalToday | ariary"
        [hint]="report().bySeller.length + ' vendeur(s) actif(s)'"
      />
      <app-kpi-card label="Panier moyen" [value]="report().averageBasket | ariary" />
      <app-kpi-card
        label="Meilleur vendeur"
        [value]="report().topSeller?.sellerName ?? '—'"
        [hint]="(report().topSeller?.amount ?? 0) | ariary"
      />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Chiffre d'affaires par vendeur
        <small>mis à jour en temps réel à chaque vente</small>
      </h2>
      <app-revenue-bars [data]="report().bySeller" />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Répartition par mode de paiement <small>encaissements du jour</small></h2>
      @if (report().byPaymentMethod.length) {
        <table>
          <thead>
            <tr>
              <th>Mode de paiement</th>
              <th class="num">Encaissements</th>
              <th class="num">Montant</th>
            </tr>
          </thead>
          <tbody>
            @for (row of report().byPaymentMethod; track row.paymentMethod) {
              <tr>
                <td>{{ methodLabels[row.paymentMethod] }}</td>
                <td class="num">{{ row.count }}</td>
                <td class="num">{{ row.amount | ariary }}</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty">Aucun encaissement aujourd'hui.</div>
      }
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Détail des ventes encaissées aujourd'hui</h2>
      <app-invoice-table
        [invoices]="paidToday()"
        [showDate]="true"
        emptyMessage="Aucune vente encaissée aujourd'hui."
      />
    </div>
  `,
})
export class RevenueComponent {
  private readonly dashboardApi = inject(DashboardApi);
  private readonly invoices = inject(InvoiceStore);

  protected readonly methodLabels = PAYMENT_METHOD_LABELS;

  private readonly reportResource = apiResource(EMPTY, () => this.dashboardApi.revenue());
  private readonly paidTodayList = this.invoices.list(() => ({
    todayOnly: true,
    paymentStatus: PaymentStatus.PAID,
  }));

  protected readonly report = this.reportResource.value;
  protected readonly paidToday = this.paidTodayList.value;

  constructor() {
    // The totals are the server's: read again when an order is paid, the list follows the store.
    reloadOnOrderChange(this.reportResource);
  }
}
