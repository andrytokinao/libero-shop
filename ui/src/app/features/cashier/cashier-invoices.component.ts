import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Invoice, PAYMENT_METHOD_LABELS } from '../../core/models';
import { SessionService } from '../../core/services/session.service';
import { ShopStore } from '../../core/services/shop-store.service';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-cashier-invoices',
  standalone: true,
  imports: [FormsModule, DatePipe, InvoiceTableComponent, AriaryPipe],
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
            (ngModelChange)="search.set($event)"
          />
        </div>
      </div>
      <app-invoice-table
        [invoices]="filtered()"
        [showSeller]="false"
        [showDate]="true"
        [actionLabel]="printLabel"
        (action)="print($event)"
        emptyMessage="Aucune facture ne correspond à la recherche."
      />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Encaissements que j'ai enregistrés</h2>
      @if (myPayments().length) {
        <table>
          <thead>
            <tr>
              <th>N° facture</th>
              <th>Date</th>
              <th>Mode</th>
              <th class="num">Montant</th>
            </tr>
          </thead>
          <tbody>
            @for (payment of myPayments(); track payment.id) {
              <tr>
                <td>{{ payment.invoice.invoiceNumber }}</td>
                <td class="muted">{{ payment.paymentDate | date: 'dd/MM HH:mm' }}</td>
                <td>{{ methodLabels[payment.paymentMethod] }}</td>
                <td class="num">{{ payment.amount | ariary }}</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty">Aucun encaissement enregistré.</div>
      }
    </div>
  `,
})
export class CashierInvoicesComponent {
  private readonly store = inject(ShopStore);
  private readonly session = inject(SessionService);
  private readonly toasts = inject(ToastService);

  protected readonly methodLabels = PAYMENT_METHOD_LABELS;
  protected readonly search = signal('');

  protected readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const invoices = this.store.invoicesOf(this.session.currentUser());
    if (!term) {
      return invoices;
    }
    return invoices.filter(
      (i) =>
        i.invoiceNumber.toLowerCase().includes(term) ||
        i.clientName.toLowerCase().includes(term),
    );
  });

  protected readonly myPayments = computed(() =>
    this.store.payments().filter((p) => p.collectedBy.id === this.session.currentUser().id),
  );

  protected readonly printLabel = (invoice: Invoice): string =>
    invoice.printed ? 'Réimprimer' : 'Imprimer';

  protected print(invoice: Invoice): void {
    this.store.markInvoicePrinted(invoice.id);
    this.toasts.show(`Facture ${invoice.invoiceNumber} envoyée à l'impression.`);
  }
}
