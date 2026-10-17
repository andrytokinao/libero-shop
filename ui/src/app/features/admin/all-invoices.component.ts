import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { InvoiceStore } from '../../core/store/invoice.store';
import { DeliveryStatus, PaymentStatus } from '../../core/models';
import { debouncedAction } from '../../core/utils/debounced-action';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { Period, PeriodFilterComponent } from '../../shared/components/period-filter.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

const FILTERS = [
  'ALL',
  'UNPAID',
  'COLLECTED',
  'REMITTED',
  'PENDING_DELIVERY',
  'IN_PROGRESS',
  'DELIVERED',
  'CANCELLED',
] as const;

type InvoiceFilter = (typeof FILTERS)[number];

/** The filters that are a payment status, and which one. */
const PAYMENT_FILTERS: Partial<Record<InvoiceFilter, PaymentStatus>> = {
  UNPAID: PaymentStatus.UNPAID,
  COLLECTED: PaymentStatus.COLLECTED,
  REMITTED: PaymentStatus.REMITTED,
  CANCELLED: PaymentStatus.CANCELLED,
};

/** The filters that are a delivery status, and which one. */
const DELIVERY_FILTERS: Partial<Record<InvoiceFilter, DeliveryStatus>> = {
  PENDING_DELIVERY: DeliveryStatus.PENDING,
  IN_PROGRESS: DeliveryStatus.IN_PROGRESS,
  DELIVERED: DeliveryStatus.DELIVERED,
};

/** The filter named in the address, if it is one of the list's; every invoice otherwise. */
function filterFrom(asked: string | null): InvoiceFilter {
  return asked !== null && FILTERS.includes(asked as InvoiceFilter) ? (asked as InvoiceFilter) : 'ALL';
}

@Component({
  selector: 'app-all-invoices',
  standalone: true,
  imports: [FormsModule, KpiCardComponent, InvoiceTableComponent, PeriodFilterComponent, AriaryPipe],
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
      <h2>
        Toutes les factures
        <small>
          tous vendeurs —
          {{ period().from || period().to ? 'période choisie' : '30 derniers jours et factures encore ouvertes' }}
        </small>
      </h2>
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
            <option value="IN_PROGRESS">En cours de service</option>
            <option value="DELIVERED">Remises</option>
            <option value="CANCELLED">Annulées</option>
          </select>
        </div>
        <app-period-filter name="all-invoice" (changed)="onPeriod($event)" />
      </div>
      <app-invoice-table
        [state]="resource"
        [invoices]="invoices()"
        [showDate]="true"
        [allowCancel]="true"
        emptyMessage="Aucune facture ne correspond aux critères."
      />
    </div>
  `,
})
export class AllInvoicesComponent {
  private readonly invoiceStore = inject(InvoiceStore);

  protected readonly search = signal('');
  /** A dashboard counter opens this list filtered (?filtre=UNPAID). */
  protected readonly filter = signal<InvoiceFilter>(filterFrom(inject(ActivatedRoute).snapshot.queryParamMap.get('filtre')));

  protected readonly period = signal<Period>({});

  // Search, filter and period are all server-side; the UI only maps its labels onto the
  // paymentStatus / deliveryStatus the API already understands.
  protected readonly resource = this.invoiceStore.list(() => ({
    ...this.period(),
    search: this.search(),
    paymentStatus: PAYMENT_FILTERS[this.filter()],
    deliveryStatus: DELIVERY_FILTERS[this.filter()],
  }));
  protected readonly invoices = this.resource.value;

  protected readonly totalAmount = computed(() =>
    this.invoices().reduce((total, invoice) => total + invoice.sale.totalAmount, 0),
  );

  protected readonly printedCount = computed(
    () => this.invoices().filter((invoice) => invoice.printed).length,
  );

  /** Asked once typing pauses, not at every keystroke. */
  private readonly reloadSoon = debouncedAction(() => this.resource.reload());

  protected onSearch(value: string): void {
    this.search.set(value);
    this.reloadSoon();
  }

  protected onPeriod(value: Period): void {
    this.period.set(value);
    this.resource.reload();
  }

  protected onFilter(value: InvoiceFilter): void {
    this.filter.set(value);
    this.resource.reload();
  }
}
