import { Component, computed, inject } from '@angular/core';
import { PaymentStatus } from '../../core/models';
import { SessionService } from '../../core/services/session.service';
import { ShopStore } from '../../core/services/shop-store.service';
import { isToday } from '../../core/utils/date.util';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-cashier-dashboard',
  standalone: true,
  imports: [KpiCardComponent, InvoiceTableComponent, AriaryPipe],
  template: `
    <div class="grid g4">
      <app-kpi-card
        label="Mon chiffre d'affaires aujourd'hui"
        [value]="revenueToday() | ariary"
        [hint]="paidSalesToday().length + ' vente(s) encaissée(s)'"
      />
      <app-kpi-card
        label="Factures émises aujourd'hui"
        [value]="invoicesToday().length"
        [hint]="'dont ' + unpaidCount() + ' non payée(s)'"
      />
      <app-kpi-card label="Panier moyen" [value]="averageBasket() | ariary" />
      <app-kpi-card
        label="Produits en alerte stock"
        [value]="store.lowStockProducts().length"
        hint="stock inférieur à 10"
      />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Mes dernières factures</h2>
      <app-invoice-table
        [invoices]="latestInvoices()"
        [showSeller]="false"
        [showDate]="true"
        emptyMessage="Vous n'avez émis aucune facture."
      />
    </div>
  `,
})
export class CashierDashboardComponent {
  protected readonly store = inject(ShopStore);
  private readonly session = inject(SessionService);

  private readonly myInvoices = computed(() => this.store.invoicesOf(this.session.currentUser()));

  protected readonly invoicesToday = computed(() =>
    this.myInvoices().filter((i) => isToday(i.invoiceDate)),
  );

  protected readonly paidSalesToday = computed(() =>
    this.store
      .salesOf(this.session.currentUser())
      .filter((s) => isToday(s.saleDate) && s.paymentStatus === PaymentStatus.PAID),
  );

  protected readonly revenueToday = computed(() =>
    this.paidSalesToday().reduce((total, sale) => total + sale.totalAmount, 0),
  );

  protected readonly unpaidCount = computed(
    () => this.invoicesToday().filter((i) => i.paymentStatus === PaymentStatus.UNPAID).length,
  );

  protected readonly averageBasket = computed(() => {
    const sales = this.paidSalesToday();
    return sales.length ? Math.round(this.revenueToday() / sales.length) : 0;
  });

  protected readonly latestInvoices = computed(() => this.myInvoices().slice(0, 6));
}
