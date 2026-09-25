import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { InvoiceApi } from '../../core/api/invoice.api';
import { DeliveryStatus, Invoice } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';
import { deliveryActionLabel } from './delivery.util';

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
            (ngModelChange)="onSearch($event)"
          />
        </div>
        <div class="fld">
          <label for="delivery-filter">Afficher</label>
          <select
            id="delivery-filter"
            class="field"
            [ngModel]="onlyPending()"
            (ngModelChange)="onFilter($event)"
          >
            <option [ngValue]="true">Commandes à remettre</option>
            <option [ngValue]="false">Toutes les commandes</option>
          </select>
        </div>
      </div>
      <app-invoice-table
        [invoices]="invoices()"
        [showDate]="true"
        [busy]="busy()"
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
  private readonly api = inject(InvoiceApi);
  private readonly toasts = inject(ToastService);

  protected readonly search = signal('');
  protected readonly onlyPending = signal(true);
  protected readonly busy = signal(false);
  protected readonly actionLabel = deliveryActionLabel;

  private readonly resource = apiResource<Invoice[]>([], () =>
    this.api.search({
      search: this.search(),
      deliveryStatus: this.onlyPending() ? DeliveryStatus.PENDING : undefined,
    }),
  );
  protected readonly invoices = this.resource.value;

  /** When the search narrows down to a single order, show its picking list. */
  protected readonly selected = computed<Invoice | null>(() => {
    const matches = this.invoices();
    return this.search().trim() && matches.length === 1 ? matches[0] : null;
  });

  protected onSearch(value: string): void {
    this.search.set(value);
    this.resource.reload();
  }

  protected onFilter(onlyPending: boolean): void {
    this.onlyPending.set(onlyPending);
    this.resource.reload();
  }

  protected deliver(invoice: Invoice): void {
    this.busy.set(true);
    this.api.deliver(invoice.id).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.resource.reload();
        this.toasts.show(result.message);
      },
      error: () => {
        this.busy.set(false);
        this.resource.reload();
      },
    });
  }
}
