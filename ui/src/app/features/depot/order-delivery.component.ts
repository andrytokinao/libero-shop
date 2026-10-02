import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InvoiceStore } from '../../core/store/invoice.store';
import { DeliveryStatus, Invoice, RoleApp } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { InvoiceTableComponent } from '../../shared/components/invoice-table.component';
import { HasRoleDirective } from '../../shared/directives/has-role.directive';
import { CashInHandComponent } from './cash-in-hand.component';
import { asksHowToPay, deliveryActionLabel, deliveryRowAction } from '../../core/orders/delivery.util';

/** The tabs of the queue: one delivery status, or every order. */
type DeliveryView = DeliveryStatus.PENDING | DeliveryStatus.IN_PROGRESS | 'ALL';
import { HandOverDialogComponent } from '../../shared/components/hand-over-dialog.component';
import { FilterTab, FilterTabsComponent } from '../../shared/components/filter-tabs.component';

@Component({
  selector: 'app-order-delivery',
  standalone: true,
  imports: [
    FormsModule,
    InvoiceTableComponent,
    CashInHandComponent,
    HasRoleDirective,
    HandOverDialogComponent,
    FilterTabsComponent,
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
      <app-filter-tabs
        label="Commandes"
        [tabs]="tabs()"
        [value]="view()"
        (valueChange)="onFilter($event)"
      />
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
      </div>
      <app-invoice-table
        [invoices]="invoices()"
        [showDate]="true"
        [busy]="busy()"
        [actionLabel]="actionLabel()"
        (action)="act($event)"
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
  private readonly invoiceStore = inject(InvoiceStore);
  private readonly toasts = inject(ToastService);

  protected readonly search = signal('');
  /** Which orders the list shows: waiting, being prepared, or all of them. */
  protected readonly view = signal<DeliveryView>(DeliveryStatus.PENDING);
  protected readonly busy = signal(false);
  protected readonly auth = inject(AuthService);
  protected readonly actionLabel = computed(() =>
    deliveryActionLabel(this.auth.words().handOverAction, this.auth.currentUser()?.id ?? null),
  );
  protected readonly RoleApp = RoleApp;
  /** The unpaid order whose "pays now or at the till?" question is open. */
  protected readonly askingFor = signal<Invoice | null>(null);

  /** Refreshed right after a hand-over, without waiting for the socket to echo it. */
  private readonly cashInHand = viewChild(CashInHandComponent);

  // Kept current by the store: a sale made at the counter appears in the queue, and an order
  // handed over by another storekeeper leaves it, without anyone pressing F5.
  private readonly resource = this.invoiceStore.list(() => {
    const view = this.view();
    return { search: this.search(), deliveryStatus: view === 'ALL' ? undefined : view };
  });
  protected readonly invoices = this.resource.value;

  /**
   * The orders waiting, and those being prepared, whatever is searched or shown — what the
   * counts on the tabs say. Kept current by the store like the list, so they move as orders come
   * in, are taken on and go out.
   */
  private readonly waiting = this.invoiceStore.list(() => ({ deliveryStatus: DeliveryStatus.PENDING }));
  private readonly inProgress = this.invoiceStore.list(() => ({ deliveryStatus: DeliveryStatus.IN_PROGRESS }));

  protected readonly tabs = computed<FilterTab<DeliveryView>[]>(() => [
    {
      value: DeliveryStatus.PENDING,
      label: this.auth.words().pendingHandOver,
      icon: 'hourglass',
      count: this.waiting.value().length,
    },
    {
      value: DeliveryStatus.IN_PROGRESS,
      label: 'En cours',
      icon: 'handOver',
      count: this.inProgress.value().length,
    },
    { value: 'ALL', label: 'Tous', icon: 'list' },
  ]);

  protected onSearch(value: string): void {
    this.search.set(value);
    this.resource.reload();
  }

  protected onFilter(view: DeliveryView): void {
    this.view.set(view);
    this.resource.reload();
  }

  /** The row's button: take the order on, or serve one already yours. */
  protected act(invoice: Invoice): void {
    if (deliveryRowAction(invoice, this.auth.currentUser()?.id ?? null) === 'take') {
      this.take(invoice);
    } else {
      this.handOver(invoice);
    }
  }

  /** "Je m'en occupe". Refused when someone was quicker: the row then shows who. */
  private take(invoice: Invoice): void {
    this.busy.set(true);
    this.invoiceStore.take(invoice.id).subscribe({
      next: (taken) => {
        this.busy.set(false);
        this.toasts.show(`Commande ${taken.invoiceNumber} : vous vous en occupez.`);
      },
      error: () => {
        this.busy.set(false);
        this.invoiceStore.refresh([invoice.id]);
      },
    });
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
    this.invoiceStore.deliver(invoice.id, collect).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.askingFor.set(null);
        this.cashInHand()?.reload();
        this.toasts.show(result.message);
      },
      error: () => {
        this.busy.set(false);
        this.askingFor.set(null);
        // Refused: perhaps handed over by someone else meanwhile — show where it really is.
        this.invoiceStore.refresh([invoice.id]);
      },
    });
  }
}
