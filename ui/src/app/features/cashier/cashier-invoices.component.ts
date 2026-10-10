import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InvoiceStore } from '../../core/store/invoice.store';
import { Invoice } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { Period, PeriodFilterComponent } from '../../shared/components/period-filter.component';

@Component({
  selector: 'app-cashier-invoices',
  standalone: true,
  imports: [FormsModule, InvoiceTableComponent, PeriodFilterComponent],
  template: `
    <div class="card">
      <h2>
        Mes factures
        <small>
          {{ period().from || period().to ? 'période choisie' : '30 derniers jours et factures encore ouvertes' }}
        </small>
      </h2>
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
        <app-period-filter name="invoice" (changed)="onPeriod($event)" />
      </div>
      <app-invoice-table
        [invoices]="invoices()"
        [showSeller]="false"
        [showDate]="true"
        [busy]="printing()"
        [actionLabel]="printLabel"
        (action)="print($event)"
        [allowCancel]="true"
        emptyMessage="Aucune facture ne correspond à la recherche."
      />
    </div>
  `,
})
export class CashierInvoicesComponent {
  private readonly invoiceStore = inject(InvoiceStore);
  private readonly toasts = inject(ToastService);

  protected readonly search = signal('');
  protected readonly printing = signal(false);

  protected readonly period = signal<Period>({});

  // Filtering happens on the server, a period at a time, so a long history never has to reach
  // the browser.
  protected readonly resource = this.invoiceStore.list(() => ({
    ...this.period(),
    mine: true,
    search: this.search(),
  }));
  protected readonly invoices = this.resource.value;

  protected onPeriod(value: Period): void {
    this.period.set(value);
    this.resource.reload();
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    this.resource.reload();
  }

  protected readonly printLabel = (invoice: Invoice): string =>
    invoice.printed ? 'Réimprimer' : 'Imprimer';

  protected print(invoice: Invoice): void {
    this.printing.set(true);
    this.invoiceStore.print(invoice.id).subscribe({
      next: () => {
        this.printing.set(false);
        this.toasts.show(`Facture ${invoice.invoiceNumber} envoyée à l'impression.`);
      },
      error: () => this.printing.set(false),
    });
  }
}
