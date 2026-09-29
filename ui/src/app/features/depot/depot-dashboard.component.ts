import { Component, inject, signal } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { DashboardApi } from '../../core/api/dashboard.api';
import { InvoiceApi } from '../../core/api/invoice.api';
import { DepotDashboard, Invoice, NotificationType } from '../../core/models';
import { reloadOn } from '../../core/realtime/reload-on';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';
import { deliveryActionLabel } from './delivery.util';

const EMPTY: DepotDashboard = {
  pendingDeliveries: 0,
  unpaidPendingDeliveries: 0,
  cashInHand: 0,
  cashInHandCount: 0,
  deliveredTotal: 0,
  deliveredToday: 0,
  deliveredValueToday: 0,
  lowStockCount: 0,
  pendingInvoices: [],
};

@Component({
  selector: 'app-depot-dashboard',
  standalone: true,
  imports: [KpiCardComponent, InvoiceTableComponent, AriaryPipe],
  template: `
    <div class="grid g4">
      <app-kpi-card label="Commandes à remettre" [value]="data().pendingDeliveries" />
      <app-kpi-card
        label="Dont non payées"
        [value]="data().unpaidPendingDeliveries"
        hint="remise autorisée, paiement au comptant"
      />
      <app-kpi-card
        label="Espèces en main"
        [value]="data().cashInHand | ariary"
        [hint]="data().cashInHandCount + ' encaissement(s) à verser'"
      />
      <app-kpi-card
        label="Remises effectuées"
        [value]="data().deliveredTotal"
        [hint]="data().deliveredToday + ' aujourd\\'hui'"
      />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Commandes en attente de remise</h2>
      <app-invoice-table
        [invoices]="data().pendingInvoices"
        [showDate]="true"
        [busy]="busy()"
        [actionLabel]="actionLabel"
        (action)="deliver($event)"
        emptyMessage="Aucune commande en attente."
      />
    </div>
  `,
})
export class DepotDashboardComponent {
  private readonly dashboardApi = inject(DashboardApi);
  private readonly invoiceApi = inject(InvoiceApi);
  private readonly toasts = inject(ToastService);

  protected readonly actionLabel = deliveryActionLabel;
  protected readonly busy = signal(false);

  private readonly resource = apiResource(EMPTY, () => this.dashboardApi.depot());
  protected readonly data = this.resource.value;

  constructor() {
    reloadOn(this.resource, NotificationType.SALE_CREATED);
  }

  protected deliver(invoice: Invoice): void {
    this.busy.set(true);
    this.invoiceApi.deliver(invoice.id).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.resource.reload();
        // The wording comes from the server, which owns the "settled on hand-over" rule.
        this.toasts.show(result.message);
      },
      error: () => {
        this.busy.set(false);
        this.resource.reload();
      },
    });
  }
}
