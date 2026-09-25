import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DeliveryStatus, PaymentStatus } from '../../core/models';
import { ShopStore } from '../../core/services/shop-store.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

type InvoiceFilter = 'ALL' | 'UNPAID' | 'PENDING_DELIVERY' | 'DELIVERED';

@Component({
  selector: 'app-all-invoices',
  standalone: true,
  imports: [FormsModule, KpiCardComponent, InvoiceTableComponent, AriaryPipe],
  template: `
    <div class="grid g3">
      <app-kpi-card
        label="Factures affichées"
        [value]="filtered().length"
        [hint]="'sur ' + store.invoices().length + ' au total'"
      />
      <app-kpi-card label="Montant affiché" [value]="filteredAmount() | ariary" />
      <app-kpi-card
        label="Factures imprimées"
        [value]="printedCount()"
        [hint]="'sur ' + store.invoices().length"
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
            (ngModelChange)="search.set($event)"
          />
        </div>
        <div class="fld">
          <label for="all-invoice-filter">Filtre</label>
          <select
            id="all-invoice-filter"
            class="field"
            [ngModel]="filter()"
            (ngModelChange)="filter.set($event)"
          >
            <option value="ALL">Toutes</option>
            <option value="UNPAID">Non payées</option>
            <option value="PENDING_DELIVERY">En attente de remise</option>
            <option value="DELIVERED">Remises</option>
          </select>
        </div>
      </div>
      <app-invoice-table
        [invoices]="filtered()"
        [showDate]="true"
        emptyMessage="Aucune facture ne correspond aux critères."
      />
    </div>
  `,
})
export class AllInvoicesComponent {
  protected readonly store = inject(ShopStore);

  protected readonly search = signal('');
  protected readonly filter = signal<InvoiceFilter>('ALL');

  protected readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const mode = this.filter();

    return this.store.invoices().filter((invoice) => {
      if (mode === 'UNPAID' && invoice.paymentStatus !== PaymentStatus.UNPAID) {
        return false;
      }
      if (mode === 'PENDING_DELIVERY' && invoice.deliveryStatus !== DeliveryStatus.PENDING) {
        return false;
      }
      if (mode === 'DELIVERED' && invoice.deliveryStatus !== DeliveryStatus.DELIVERED) {
        return false;
      }
      if (!term) {
        return true;
      }
      return [invoice.invoiceNumber, invoice.clientName, invoice.sale.seller.fullName].some(
        (field) => field.toLowerCase().includes(term),
      );
    });
  });

  protected readonly filteredAmount = computed(() =>
    this.filtered().reduce((total, i) => total + i.sale.totalAmount, 0),
  );

  protected readonly printedCount = computed(
    () => this.store.invoices().filter((i) => i.printed).length,
  );
}
