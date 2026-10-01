import { Component, computed, inject, signal } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { InvoiceApi } from '../../core/api/invoice.api';
import { Invoice, PaymentMethod, PaymentStatus } from '../../core/models';
import { ORDERS_TOPIC, reloadOnTopic } from '../../core/realtime/reload-on';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { PayDialogComponent } from '../cashier/pay-dialog.component';

/**
 * The orders this person took today, with where each one is: served or waiting, paid or not.
 * Follows the other screens live, so "Remise" turns green the moment it is handed over.
 *
 * <p>When the shop lets order takers take money, an unpaid order can be cashed from here — the
 * guest who comes to the reception to pay. The cash then waits in "Argent à remettre".
 */
@Component({
  selector: 'app-my-orders',
  standalone: true,
  imports: [InvoiceTableComponent, PayDialogComponent],
  template: `
    <div class="card">
      <h2>
        Mes commandes du jour
        <small>ouvrez le détail d'une commande pour l'annuler si besoin</small>
      </h2>
      <app-invoice-table
        [invoices]="invoices()"
        [showSeller]="false"
        [showDate]="true"
        [busy]="busy()"
        [actionLabel]="collectLabel()"
        (action)="selected.set($event)"
        [allowCancel]="true"
        (changed)="resource.reload()"
        emptyMessage="Aucune commande prise aujourd'hui."
      />
    </div>

    @if (selected(); as invoice) {
      <app-pay-dialog
        [invoice]="invoice"
        [methods]="cashOnly"
        [busy]="busy()"
        (paid)="collect(invoice)"
        (closed)="selected.set(null)"
      />
    }
  `,
})
export class MyOrdersComponent {
  private readonly api = inject(InvoiceApi);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);

  protected readonly busy = signal(false);
  protected readonly selected = signal<Invoice | null>(null);
  protected readonly cashOnly = [PaymentMethod.CASH];

  protected readonly resource = apiResource<Invoice[]>([], () =>
    this.api.search({ mine: true, todayOnly: true }),
  );
  protected readonly invoices = this.resource.value;

  /** "Encaisser" on the unpaid ones, only where the shop lets order takers take money. */
  protected readonly collectLabel = computed(() =>
    this.auth.settings().orderTakerCollects
      ? (invoice: Invoice) => (invoice.paymentStatus === PaymentStatus.UNPAID ? 'Encaisser' : null)
      : undefined,
  );

  constructor() {
    reloadOnTopic(this.resource, ORDERS_TOPIC);
  }

  protected collect(invoice: Invoice): void {
    this.busy.set(true);
    this.api.collect(invoice.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.selected.set(null);
        this.resource.reload();
        this.toasts.show(
          `${invoice.invoiceNumber} encaissée : ${invoice.sale.totalAmount.toLocaleString('fr-FR')} Ar ` +
            'à remettre à la caisse.',
        );
      },
      error: () => {
        this.busy.set(false);
        this.selected.set(null);
        this.resource.reload();
      },
    });
  }
}
