import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { InvoiceApi } from '../../core/api/invoice.api';
import { DeliveryStatus, Invoice, PaymentStatus } from '../../core/models';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

type InvoiceFilter =
  | 'ALL'
  | 'UNPAID'
  | 'COLLECTED'
  | 'REMITTED'
  | 'PENDING_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED';

/** The filters that are a payment status, and which one. */
const PAYMENT_FILTERS: Partial<Record<InvoiceFilter, PaymentStatus>> = {
  UNPAID: PaymentStatus.UNPAID,
  COLLECTED: PaymentStatus.COLLECTED,
  REMITTED: PaymentStatus.REMITTED,
  CANCELLED: PaymentStatus.CANCELLED,
};

@Component({
  selector: 'app-all-invoices',
  standalone: true,
  imports: [FormsModule, KpiCardComponent, InvoiceTableComponent, AriaryPipe],
  template: `
    <div class="grid g3">
      <app-kpi-card label="Factures affichées" [value]="invoices().length" />
      <app-kpi-card label="Montant affiché" [value]="totalAmount() | ariary" />
      <app-kpi-card
        label="Factures imprimées"
        [value]="printedCount()"
        [hint]="'sur ' + invoices().length + ' affichée(s)'"
      />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Toutes les factures <small>tous vendeurs, toutes dates</small></h2>
      <div class="form-row">
        <div class="fld" style="flex:1; max-width:300px;">
          <label for="all-invoice-search">Recherche</label>
          <input
            id="all-invoice-search"
            type="text"
            placeholder="Numéro, client ou vendeur"
            [ngModel]="search()"
            (ngModelChange)="onSearch($event)"
          />
        </div>
        <div class="fld">
          <label for="all-invoice-filter">Filtre</label>
          <select
            id="all-invoice-filter"
            class="field"
            [ngModel]="filter()"
            (ngModelChange)="onFilter($event)"
          >
            <option value="ALL">Toutes</option>
            <option value="UNPAID">Non payées</option>
            <option value="COLLECTED">Encaissées, argent pas encore remis à la caisse</option>
            <option value="REMITTED">Versées, à confirmer par la caisse</option>
            <option value="PENDING_DELIVERY">En attente de remise</option>
            <option value="DELIVERED">Remises</option>
            <option value="CANCELLED">Annulées</option>
          </select>
        </div>
      </div>
      <app-invoice-table
        [invoices]="invoices()"
        [showDate]="true"
        [allowCancel]="true"
        (changed)="resource.reload()"
        emptyMessage="Aucune facture ne correspond aux critères."
      />
    </div>
  `,
})
export class AllInvoicesComponent {
  private readonly api = inject(InvoiceApi);

  protected readonly search = signal('');
  protected readonly filter = signal<InvoiceFilter>('ALL');

  // Search and filter are both server-side; the UI only maps its labels onto the
  // paymentStatus / deliveryStatus the API already understands.
  protected readonly resource = apiResource<Invoice[]>([], () =>
    this.api.search({
      search: this.search(),
      paymentStatus: PAYMENT_FILTERS[this.filter()],
      deliveryStatus:
        this.filter() === 'PENDING_DELIVERY'
          ? DeliveryStatus.PENDING
          : this.filter() === 'DELIVERED'
            ? DeliveryStatus.DELIVERED
            : undefined,
    }),
  );
  protected readonly invoices = this.resource.value;

  protected readonly totalAmount = computed(() =>
    this.invoices().reduce((total, invoice) => total + invoice.sale.totalAmount, 0),
  );

  protected readonly printedCount = computed(
    () => this.invoices().filter((invoice) => invoice.printed).length,
  );

  protected onSearch(value: string): void {
    this.search.set(value);
    this.resource.reload();
  }

  protected onFilter(value: InvoiceFilter): void {
    this.filter.set(value);
    this.resource.reload();
  }
}
