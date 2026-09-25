import { Component, computed, inject } from '@angular/core';
import { ShopStore } from '../../core/services/shop-store.service';
import { isToday } from '../../core/utils/date.util';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-delivery-history',
  standalone: true,
  imports: [KpiCardComponent, InvoiceTableComponent, AriaryPipe],
  template: `
    <div class="grid g3">
      <app-kpi-card label="Remises du jour" [value]="deliveredToday().length" />
      <app-kpi-card label="Total remis (toutes dates)" [value]="delivered().length" />
      <app-kpi-card label="Valeur remise aujourd'hui" [value]="valueToday() | ariary" />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Historique des remises</h2>
      <app-invoice-table
        [invoices]="delivered()"
        [showDate]="true"
        emptyMessage="Aucune commande remise pour le moment."
      />
    </div>
  `,
})
export class DeliveryHistoryComponent {
  private readonly store = inject(ShopStore);

  protected readonly delivered = this.store.deliveredInvoices;

  protected readonly deliveredToday = computed(() =>
    this.delivered().filter((i) => isToday(i.invoiceDate)),
  );

  protected readonly valueToday = computed(() =>
    this.deliveredToday().reduce((total, i) => total + i.sale.totalAmount, 0),
  );
}
