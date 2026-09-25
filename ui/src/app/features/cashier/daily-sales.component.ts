import { Component, computed, inject } from '@angular/core';
import { PaymentStatus } from '../../core/models';
import { SessionService } from '../../core/services/session.service';
import { ShopStore } from '../../core/services/shop-store.service';
import { isToday } from '../../core/utils/date.util';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-daily-sales',
  standalone: true,
  imports: [KpiCardComponent, InvoiceTableComponent, AriaryPipe],
  template: `
    <div class="grid g3">
      <app-kpi-card
        label="Ventes du jour"
        [value]="invoicesToday().length"
        [hint]="session.currentUser().fullName"
      />
      <app-kpi-card label="Total encaissé" [value]="collected() | ariary" />
      <app-kpi-card
        label="Reste à encaisser au dépôt"
        [value]="outstanding() | ariary"
        [hint]="unpaid().length + ' facture(s) non payée(s)'"
      />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Détail des ventes du jour</h2>
      <app-invoice-table
        [invoices]="invoicesToday()"
        [showSeller]="false"
        [showDate]="true"
        emptyMessage="Aucune vente enregistrée aujourd'hui."
      />
    </div>
  `,
})
export class DailySalesComponent {
  private readonly store = inject(ShopStore);
  protected readonly session = inject(SessionService);

  protected readonly invoicesToday = computed(() =>
    this.store.invoicesOf(this.session.currentUser()).filter((i) => isToday(i.invoiceDate)),
  );

  private readonly paid = computed(() =>
    this.invoicesToday().filter((i) => i.paymentStatus === PaymentStatus.PAID),
  );

  protected readonly unpaid = computed(() =>
    this.invoicesToday().filter((i) => i.paymentStatus === PaymentStatus.UNPAID),
  );

  protected readonly collected = computed(() =>
    this.paid().reduce((total, i) => total + i.sale.totalAmount, 0),
  );

  protected readonly outstanding = computed(() =>
    this.unpaid().reduce((total, i) => total + i.sale.totalAmount, 0),
  );
}
