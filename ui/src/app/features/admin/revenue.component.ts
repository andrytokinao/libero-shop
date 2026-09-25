import { Component, computed, inject } from '@angular/core';
import { PAYMENT_METHOD_LABELS, PaymentMethod, PaymentStatus } from '../../core/models';
import { ShopStore } from '../../core/services/shop-store.service';
import { isToday } from '../../core/utils/date.util';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { RevenueBarsComponent } from '../../shared/components/revenue-bars.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-revenue',
  standalone: true,
  imports: [KpiCardComponent, RevenueBarsComponent, InvoiceTableComponent, AriaryPipe],
  template: `
    <div class="grid g3">
      <app-kpi-card
        label="Chiffre d'affaires du jour"
        [value]="store.revenueToday() | ariary"
        [hint]="store.revenueBySeller().length + ' vendeur(s) actif(s)'"
      />
      <app-kpi-card label="Panier moyen" [value]="averageBasket() | ariary" />
      <app-kpi-card
        label="Meilleur vendeur"
        [value]="topSeller()"
        [hint]="topSellerAmount() | ariary"
      />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Chiffre d'affaires par vendeur
        <small>mis à jour en temps réel à chaque vente</small>
      </h2>
      <app-revenue-bars [data]="store.revenueBySeller()" />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Répartition par mode de paiement <small>encaissements du jour</small></h2>
      @if (byMethod().length) {
        <table>
          <thead>
            <tr>
              <th>Mode de paiement</th>
              <th class="num">Encaissements</th>
              <th class="num">Montant</th>
            </tr>
          </thead>
          <tbody>
            @for (row of byMethod(); track row.method) {
              <tr>
                <td>{{ methodLabels[row.method] }}</td>
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
  protected readonly store = inject(ShopStore);
  protected readonly methodLabels = PAYMENT_METHOD_LABELS;

  protected readonly paidToday = computed(() =>
    this.store
      .invoices()
      .filter((i) => isToday(i.invoiceDate) && i.paymentStatus === PaymentStatus.PAID),
  );

  protected readonly averageBasket = computed(() => {
    const rows = this.store.revenueBySeller();
    const sales = rows.reduce((total, row) => total + row.saleCount, 0);
    return sales ? Math.round(this.store.revenueToday() / sales) : 0;
  });

  protected readonly topSeller = computed(
    () => this.store.revenueBySeller()[0]?.seller.fullName ?? '—',
  );

  protected readonly topSellerAmount = computed(
    () => this.store.revenueBySeller()[0]?.amount ?? 0,
  );

  protected readonly byMethod = computed(() => {
    const grouped = new Map<PaymentMethod, { method: PaymentMethod; count: number; amount: number }>();
    for (const payment of this.store.payments().filter((p) => isToday(p.paymentDate))) {
      const current = grouped.get(payment.paymentMethod) ?? {
        method: payment.paymentMethod,
        count: 0,
        amount: 0,
      };
      grouped.set(payment.paymentMethod, {
        method: payment.paymentMethod,
        count: current.count + 1,
        amount: current.amount + payment.amount,
      });
    }
    return [...grouped.values()].sort((a, b) => b.amount - a.amount);
  });
}
