import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Invoice } from '../../core/models';
import { SessionService } from '../../core/services/session.service';
import { ShopStore } from '../../core/services/shop-store.service';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';
import { deliveryActionLabel, describeDelivery } from './delivery.util';

@Component({
  selector: 'app-order-delivery',
  standalone: true,
  imports: [FormsModule, InvoiceTableComponent, AriaryPipe],
  template: `
    <div class="card">
      <h2>
        Remise de commande
        <small>recherchez par n° de facture ou nom du client</small>
      </h2>
      <div class="form-row">
        <div class="fld" style="flex:1; max-width:320px;">
          <label for="delivery-search">Recherche</label>
          <input
            id="delivery-search"
            type="text"
            placeholder="Ex : F-1031 ou Rina"
            [ngModel]="search()"
            (ngModelChange)="search.set($event)"
          />
        </div>
        <div class="fld">
          <label for="delivery-filter">Afficher</label>
          <select
            id="delivery-filter"
            class="field"
            [ngModel]="onlyPending()"
            (ngModelChange)="onlyPending.set($event)"
          >
            <option [ngValue]="true">Commandes à remettre</option>
            <option [ngValue]="false">Toutes les commandes</option>
          </select>
        </div>
      </div>
      <app-invoice-table
        [invoices]="filtered()"
        [showDate]="true"
        [actionLabel]="actionLabel"
        (action)="deliver($event)"
        emptyMessage="Aucune commande ne correspond à la recherche."
      />
    </div>

    @if (selected(); as invoice) {
      <div class="card" style="margin-top:16px;">
        <h2>
          Détail de {{ invoice.invoiceNumber }}
          <small>{{ invoice.clientName }} — articles à préparer</small>
        </h2>
        <table>
          <thead>
            <tr>
              <th>Produit</th>
              <th class="num">Quantité</th>
              <th class="num">Prix unitaire</th>
              <th class="num">Sous-total</th>
            </tr>
          </thead>
          <tbody>
            @for (line of invoice.sale.lines; track line.id) {
              <tr>
                <td>{{ line.product.name }}</td>
                <td class="num">{{ line.quantity }}</td>
                <td class="num">{{ line.unitPrice | ariary }}</td>
                <td class="num">{{ line.unitPrice * line.quantity | ariary }}</td>
              </tr>
            }
          </tbody>
        </table>
        <div class="total-row">
          <span>Total</span>
          <span>{{ invoice.sale.totalAmount | ariary }}</span>
        </div>
      </div>
    }
  `,
})
export class OrderDeliveryComponent {
  private readonly store = inject(ShopStore);
  private readonly session = inject(SessionService);
  private readonly toasts = inject(ToastService);

  protected readonly search = signal('');
  protected readonly onlyPending = signal(true);
  protected readonly actionLabel = deliveryActionLabel;

  protected readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const base = this.onlyPending() ? this.store.pendingDeliveries() : this.store.invoices();
    if (!term) {
      return base;
    }
    return base.filter(
      (i) =>
        i.invoiceNumber.toLowerCase().includes(term) || i.clientName.toLowerCase().includes(term),
    );
  });

  /** When the search narrows down to a single order, show its picking list. */
  protected readonly selected = computed<Invoice | null>(() => {
    const matches = this.filtered();
    return this.search().trim() && matches.length === 1 ? matches[0] : null;
  });

  protected deliver(invoice: Invoice): void {
    const result = this.store.deliverInvoice(invoice.id, this.session.currentUser());
    if (result) {
      this.toasts.show(describeDelivery(result));
    }
  }
}
