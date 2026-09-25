import { Component, computed, inject } from '@angular/core';
import { Invoice, PaymentStatus } from '../../core/models';
import { SessionService } from '../../core/services/session.service';
import { ShopStore } from '../../core/services/shop-store.service';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';
import { deliveryActionLabel, describeDelivery } from './delivery.util';

@Component({
  selector: 'app-depot-dashboard',
  standalone: true,
  imports: [KpiCardComponent, InvoiceTableComponent, AriaryPipe],
  template: `
    <div class="grid g4">
      <app-kpi-card label="Commandes à remettre" [value]="pending().length" />
      <app-kpi-card
        label="Dont non payées"
        [value]="unpaidPending().length"
        hint="remise autorisée, paiement au comptant"
      />
      <app-kpi-card
        label="Espèces en main"
        [value]="cashInHand() | ariary"
        hint="à verser à la caisse"
      />
      <app-kpi-card label="Remises effectuées" [value]="store.deliveredInvoices().length" />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Commandes en attente de remise</h2>
      <app-invoice-table
        [invoices]="pending()"
        [showDate]="true"
        [actionLabel]="actionLabel"
        (action)="deliver($event)"
        emptyMessage="Aucune commande en attente."
      />
    </div>
  `,
})
export class DepotDashboardComponent {
  protected readonly store = inject(ShopStore);
  private readonly session = inject(SessionService);
  private readonly toasts = inject(ToastService);

  protected readonly pending = this.store.pendingDeliveries;
  protected readonly actionLabel = deliveryActionLabel;

  protected readonly unpaidPending = computed(() =>
    this.pending().filter((i) => i.paymentStatus === PaymentStatus.UNPAID),
  );

  protected readonly cashInHand = computed(() =>
    ShopStore.total(this.store.cashInHandOf(this.session.currentUser())),
  );

  protected deliver(invoice: Invoice): void {
    const result = this.store.deliverInvoice(invoice.id, this.session.currentUser());
    if (result) {
      this.toasts.show(describeDelivery(result));
    }
  }
}
