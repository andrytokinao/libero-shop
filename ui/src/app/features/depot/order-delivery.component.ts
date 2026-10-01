import { Component, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { InvoiceApi } from '../../core/api/invoice.api';
import { DeliveryStatus, Invoice, RoleApp } from '../../core/models';
import { ORDERS_TOPIC, reloadOnTopic } from '../../core/realtime/reload-on';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { HasRoleDirective } from '../../shared/directives/has-role.directive';
import { CashInHandComponent } from './cash-in-hand.component';
import { asksHowToPay, deliveryActionLabel } from './delivery.util';
import { HandOverDialogComponent } from './hand-over-dialog.component';

@Component({
  selector: 'app-order-delivery',
  standalone: true,
  imports: [
    FormsModule,
    InvoiceTableComponent,
    CashInHandComponent,
    HasRoleDirective,
    HandOverDialogComponent,
  ],
  template: `
    <div class="card">
      <h2>
        {{ auth.words().handOver }}
        <small>
          recherchez par n° de facture ou nom du client, puis ouvrez le détail pour
          vérifier les articles avant de remettre
        </small>
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
        (action)="handOver($event)"
        emptyMessage="Aucune commande ne correspond à la recherche."
      />
    </div>

    @if (askingFor(); as invoice) {
      <app-hand-over-dialog
        [invoice]="invoice"
        [busy]="busy()"
        (chosen)="deliver(invoice, $event)"
        (closed)="askingFor.set(null)"
      />
    }

    <!-- Where an unpaid order lands once handed over: its cash, with the button to bring it
         to the desk. Only a storekeeper holds cash; a manager browsing here has none, and
         neither does anyone when the shop settles every order at the till. -->
    @if (auth.settings().payAtDepot) {
      <div *appHasRole="RoleApp.DEPOT_AGENT" style="margin-top:16px;">
        <app-cash-in-hand />
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
  protected readonly auth = inject(AuthService);
  protected readonly actionLabel = deliveryActionLabel;
  protected readonly RoleApp = RoleApp;
  /** The unpaid order whose "pays now or at the till?" question is open. */
  protected readonly askingFor = signal<Invoice | null>(null);

  /** Refreshed right after a hand-over, without waiting for the socket to echo it. */
  private readonly cashInHand = viewChild(CashInHandComponent);

  private readonly resource = apiResource<Invoice[]>([], () =>
    this.api.search({
      search: this.search(),
      deliveryStatus: this.onlyPending() ? DeliveryStatus.PENDING : undefined,
    }),
  );
  protected readonly invoices = this.resource.value;

  constructor() {
    // A sale made at the counter appears in the queue, and an order handed over by another
    // storekeeper leaves it, without anyone pressing F5.
    reloadOnTopic(this.resource, ORDERS_TOPIC);
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    this.resource.reload();
  }

  protected onFilter(onlyPending: boolean): void {
    this.onlyPending.set(onlyPending);
    this.resource.reload();
  }

  protected handOver(invoice: Invoice): void {
    if (asksHowToPay(invoice, this.auth.settings().payAtDepot)) {
      this.askingFor.set(invoice);
    } else {
      this.deliver(invoice, true);
    }
  }

  /** @param collect the customer pays now; false: served, the bill is left to the till */
  protected deliver(invoice: Invoice, collect: boolean): void {
    this.busy.set(true);
    this.api.deliver(invoice.id, collect).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.askingFor.set(null);
        this.resource.reload();
        this.cashInHand()?.reload();
        this.toasts.show(result.message);
      },
      error: () => {
        this.busy.set(false);
        this.askingFor.set(null);
        this.resource.reload();
      },
    });
  }
}
