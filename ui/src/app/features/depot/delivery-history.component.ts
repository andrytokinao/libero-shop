import { Component, computed, inject } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { InvoiceApi } from '../../core/api/invoice.api';
import { DeliveryStatus, Invoice } from '../../core/models';
import { ORDERS_TOPIC, reloadOnTopic } from '../../core/realtime/reload-on';
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
  private readonly api = inject(InvoiceApi);

  private readonly all = apiResource<Invoice[]>([], () =>
    this.api.search({ deliveryStatus: DeliveryStatus.DELIVERED }),
  );
  private readonly today = apiResource<Invoice[]>([], () =>
    this.api.search({ deliveryStatus: DeliveryStatus.DELIVERED, todayOnly: true }),
  );

  constructor() {
    reloadOnTopic(this.all, ORDERS_TOPIC);
    reloadOnTopic(this.today, ORDERS_TOPIC);
  }

  protected readonly delivered = this.all.value;
  protected readonly deliveredToday = this.today.value;

  protected readonly valueToday = computed(() =>
    this.deliveredToday().reduce((total, invoice) => total + invoice.sale.totalAmount, 0),
  );
}
