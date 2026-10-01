import { Component, inject, signal } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { DashboardApi } from '../../core/api/dashboard.api';
import { InvoiceApi } from '../../core/api/invoice.api';
import { DepotDashboard, Invoice } from '../../core/models';
import { ORDERS_TOPIC, reloadOnTopic } from '../../core/realtime/reload-on';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';
import { asksHowToPay, deliveryActionLabel } from './delivery.util';
import { HandOverDialogComponent } from './hand-over-dialog.component';

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
  imports: [KpiCardComponent, InvoiceTableComponent, AriaryPipe, HandOverDialogComponent],
  template: `
    <div class="grid g4">
      <app-kpi-card label="Commandes à remettre" [value]="data().pendingDeliveries" />
      <app-kpi-card
        label="Dont non payées"
        [value]="data().unpaidPendingDeliveries"
        [hint]="
          auth.settings().payAtDepot
            ? 'paiement à la remise ou à la caisse'
            : 'remise autorisée, paiement à la caisse'
        "
      />
      @if (auth.settings().payAtDepot) {
        <app-kpi-card
          label="Espèces en main"
          [value]="data().cashInHand | ariary"
          [hint]="data().cashInHandCount + ' encaissement(s) à verser'"
        />
      }
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
        (action)="handOver($event)"
        emptyMessage="Aucune commande en attente."
      />
    </div>

    @if (askingFor(); as invoice) {
      <app-hand-over-dialog
        [invoice]="invoice"
        [busy]="busy()"
        (chosen)="deliver(invoice, $event)"
        (closed)="askingFor.set(null)"
      />
    }
  `,
})
export class DepotDashboardComponent {
  private readonly dashboardApi = inject(DashboardApi);
  private readonly invoiceApi = inject(InvoiceApi);
  private readonly toasts = inject(ToastService);

  protected readonly auth = inject(AuthService);
  protected readonly actionLabel = deliveryActionLabel;
  protected readonly busy = signal(false);
  /** The unpaid order whose "pays now or at the till?" question is open. */
  protected readonly askingFor = signal<Invoice | null>(null);

  private readonly resource = apiResource(EMPTY, () => this.dashboardApi.depot());
  protected readonly data = this.resource.value;

  constructor() {
    reloadOnTopic(this.resource, ORDERS_TOPIC);
  }

  protected handOver(invoice: Invoice): void {
    if (asksHowToPay(invoice, this.auth.settings().payAtDepot)) {
      this.askingFor.set(invoice);
    } else {
      this.deliver(invoice, true);
    }
  }

  /** @param collect the customer pays now; false: served, the bill is left to the till */
  protected deliver(invoice: Invoice, collect: boolean): void {
    this.busy.set(true);
    this.invoiceApi.deliver(invoice.id, collect).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.askingFor.set(null);
        this.resource.reload();
        // The wording comes from the server, which owns the "settled on hand-over" rule.
        this.toasts.show(result.message);
      },
      error: () => {
        this.busy.set(false);
        this.askingFor.set(null);
        this.resource.reload();
      },
    });
  }
}
