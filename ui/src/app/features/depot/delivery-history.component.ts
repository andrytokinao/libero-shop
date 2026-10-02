import { Component, computed, inject } from '@angular/core';
import { InvoiceStore } from '../../core/store/invoice.store';
import { DeliveryStatus } from '../../core/models';
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
  private readonly invoiceStore = inject(InvoiceStore);

  protected readonly delivered = this.invoiceStore.list(() => ({
    deliveryStatus: DeliveryStatus.DELIVERED,
  })).value;
  protected readonly deliveredToday = this.invoiceStore.list(() => ({
    deliveryStatus: DeliveryStatus.DELIVERED,
    todayOnly: true,
  })).value;

  protected readonly valueToday = computed(() =>
    this.deliveredToday().reduce((total, invoice) => total + invoice.sale.totalAmount, 0),
  );
}
