import { Component, computed, inject, signal } from '@angular/core';
import { InvoiceStore } from '../../core/store/invoice.store';
import { DeliveryStatus } from '../../core/models';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { Period, PeriodFilterComponent } from '../../shared/components/period-filter.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-delivery-history',
  standalone: true,
  imports: [KpiCardComponent, InvoiceTableComponent, PeriodFilterComponent, AriaryPipe],
  template: `
    <div class="grid g3">
      <app-kpi-card label="Remises du jour" [value]="deliveredToday().length" />
      <app-kpi-card
        label="Remises affichées"
        [value]="delivered().length"
        [hint]="period().from || period().to ? 'période choisie' : '30 derniers jours'"
      />
      <app-kpi-card label="Valeur remise aujourd'hui" [value]="valueToday() | ariary" />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Historique des remises</h2>
      <div class="form-row">
        <app-period-filter name="delivery-history" (changed)="onPeriod($event)" />
      </div>
      <app-invoice-table
        [state]="history"
        [invoices]="delivered()"
        [showDate]="true"
        emptyMessage="Aucune commande remise pour le moment."
      />
    </div>
  `,
})
export class DeliveryHistoryComponent {
  private readonly invoiceStore = inject(InvoiceStore);

  protected readonly period = signal<Period>({});

  protected readonly history = this.invoiceStore.list(() => ({
    ...this.period(),
    deliveryStatus: DeliveryStatus.DELIVERED,
  }));
  protected readonly delivered = this.history.value;
  protected readonly deliveredToday = this.invoiceStore.list(() => ({
    deliveryStatus: DeliveryStatus.DELIVERED,
    todayOnly: true,
  })).value;

  protected readonly valueToday = computed(() =>
    this.deliveredToday().reduce((total, invoice) => total + invoice.sale.totalAmount, 0),
  );

  protected onPeriod(value: Period): void {
    this.period.set(value);
    this.history.reload();
  }
}
