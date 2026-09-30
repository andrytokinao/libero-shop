import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { InvoiceApi } from '../../core/api/invoice.api';
import { Invoice } from '../../core/models';
import { ORDERS_TOPIC, reloadOnTopic } from '../../core/realtime/reload-on';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';

@Component({
  selector: 'app-cashier-invoices',
  standalone: true,
  imports: [FormsModule, InvoiceTableComponent],
  template: `
    <div class="card">
      <h2>Mes factures <small>recherchez par numéro ou nom du client</small></h2>
      <div class="form-row">
        <div class="fld" style="flex:1; max-width:320px;">
          <label for="invoice-search">Recherche</label>
          <input
            id="invoice-search"
            type="text"
            placeholder="Ex : F-1031 ou Rina"
            [ngModel]="search()"
            (ngModelChange)="onSearch($event)"
          />
        </div>
      </div>
      <app-invoice-table
        [invoices]="invoices()"
        [showSeller]="false"
        [showDate]="true"
        [busy]="printing()"
        [actionLabel]="printLabel"
        (action)="print($event)"
        emptyMessage="Aucune facture ne correspond à la recherche."
      />
    </div>
  `,
})
export class CashierInvoicesComponent {
  private readonly api = inject(InvoiceApi);
  private readonly toasts = inject(ToastService);

  protected readonly search = signal('');
  protected readonly printing = signal(false);

  // Filtering happens on the server, so a long history never has to reach the browser.
  private readonly resource = apiResource<Invoice[]>([], () =>
    this.api.search({ mine: true, search: this.search() }),
  );
  protected readonly invoices = this.resource.value;

  constructor() {
    reloadOnTopic(this.resource, ORDERS_TOPIC);
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    this.resource.reload();
  }

  protected readonly printLabel = (invoice: Invoice): string =>
    invoice.printed ? 'Réimprimer' : 'Imprimer';

  protected print(invoice: Invoice): void {
    this.printing.set(true);
    this.api.print(invoice.id).subscribe({
      next: () => {
        this.printing.set(false);
        this.resource.reload();
        this.toasts.show(`Facture ${invoice.invoiceNumber} envoyée à l'impression.`);
      },
      error: () => this.printing.set(false),
    });
  }
}
