import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { InvoiceApi } from '../../core/api/invoice.api';
import { Invoice, PAYMENT_METHOD_LABELS, PaymentMethod, PaymentStatus } from '../../core/models';
import { ORDERS_TOPIC, reloadOnTopic } from '../../core/realtime/reload-on';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { PayDialogComponent } from '../../shared/components/pay-dialog.component';

/**
 * Every unpaid order, whoever took it: the restaurant's bills, the customer who comes back to
 * pay, the orders the depot hands over without taking money.
 */
@Component({
  selector: 'app-to-collect',
  standalone: true,
  imports: [FormsModule, InvoiceTableComponent, PayDialogComponent],
  template: `
    <div class="card">
      <h2>
        Commandes à encaisser
        <small>touchez « Encaisser » quand le client paie</small>
      </h2>
      <div class="form-row">
        <div class="fld" style="flex:1; max-width:320px;">
          <label for="collect-search">Recherche</label>
          <input
            id="collect-search"
            type="text"
            [placeholder]="'N° de facture ou ' + auth.words().client.toLowerCase()"
            [ngModel]="search()"
            (ngModelChange)="onSearch($event)"
          />
        </div>
      </div>
      <app-invoice-table
        [invoices]="invoices()"
        [showDate]="true"
        [busy]="busy()"
        [actionLabel]="payLabel"
        (action)="selected.set($event)"
        [allowCancel]="true"
        (changed)="resource.reload()"
        emptyMessage="Aucune commande à encaisser."
      />
    </div>

    @if (selected(); as invoice) {
      <app-pay-dialog
        [invoice]="invoice"
        [busy]="busy()"
        (paid)="pay(invoice, $event)"
        (closed)="selected.set(null)"
      />
    }
  `,
})
export class ToCollectComponent {
  private readonly api = inject(InvoiceApi);
  private readonly toasts = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly search = signal('');
  protected readonly busy = signal(false);
  protected readonly selected = signal<Invoice | null>(null);

  protected readonly resource = apiResource<Invoice[]>([], () =>
    this.api.search({ paymentStatus: PaymentStatus.UNPAID, search: this.search() }),
  );
  protected readonly invoices = this.resource.value;

  constructor() {
    // An order taken elsewhere appears here, and one paid at another till leaves.
    reloadOnTopic(this.resource, ORDERS_TOPIC);
  }

  protected readonly payLabel = (): string => 'Encaisser';

  protected onSearch(value: string): void {
    this.search.set(value);
    this.resource.reload();
  }

  protected pay(invoice: Invoice, method: PaymentMethod): void {
    this.busy.set(true);
    this.api.pay(invoice.id, { paymentMethod: method }).subscribe({
      next: () => {
        this.busy.set(false);
        this.selected.set(null);
        this.resource.reload();
        this.toasts.show(
          `${invoice.invoiceNumber} encaissée : ` +
            `${invoice.sale.totalAmount.toLocaleString('fr-FR')} Ar (${PAYMENT_METHOD_LABELS[method]}).`,
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
