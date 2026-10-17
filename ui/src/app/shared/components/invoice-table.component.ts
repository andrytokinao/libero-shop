import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { Invoice } from '../../core/models';
import { LoadState } from '../../core/api/load-state';
import { InvoiceActionKind, availableActions } from '../../core/orders/invoice-actions';
import { InvoiceMenuEntry, InvoiceMenuId, SHOW_DETAIL, invoiceActionContext, invoiceMenu } from '../../core/orders/invoice-menu';
import { AuthService } from '../../core/services/auth.service';
import { AriaryPipe } from '../pipes/ariary.pipe';
import { ActionMenuComponent } from './action-menu.component';
import { LoadStateComponent, showsEmpty } from './load-state.component';
import { InvoiceDetailFlowComponent } from './invoice-detail-flow.component';
import { DeliveryStatusBadgeComponent, PaymentStatusBadgeComponent } from './status-badges.component';
import { HandlerChipComponent } from './handler-chip.component';
import { UserAvatarComponent } from './user-avatar.component';

/**
 * The invoice list shared by the cash-desk, depot and admin screens.
 *
 * <p>Two renderings of the same rows: a table on a desktop, a stack of cards on a phone
 * — an invoice has nine columns, which no phone shows without sideways scrolling. Either
 * one opens the articles of an invoice in a dialog, because the depot checks the goods
 * against that list before handing an order over.
 */
@Component({
  selector: 'app-invoice-table',
  standalone: true,
  imports: [
    ActionMenuComponent,
    LoadStateComponent,
    DatePipe,
    AriaryPipe,
    PaymentStatusBadgeComponent,
    DeliveryStatusBadgeComponent,
    InvoiceDetailFlowComponent,
    UserAvatarComponent,
    HandlerChipComponent,
  ],
  template: `
    <app-load-state [of]="state" what="les factures" />
    @if (invoices.length) {
      <div class="table-scroll">
        <table class="inv-table">
          <thead>
            <tr>
              <th>N° facture</th>
              @if (showDate) {
                <th>Date</th>
              }
              <th>Client</th>
              @if (showSeller) {
                <th>Vendeur</th>
              }
              <th class="num">Articles</th>
              <th class="num">Montant</th>
              <th>Paiement</th>
              <th>Remise</th>
              @if (showDetail) {
                <th class="col-detail"></th>
              }
              @if (actionLabel) {
                <th></th>
              }
            </tr>
          </thead>
          <tbody>
            @for (invoice of invoices; track invoice.id) {
              <tr>
                <td>{{ invoice.invoiceNumber }}</td>
                @if (showDate) {
                  <td class="muted">{{ invoice.invoiceDate | date: 'dd/MM HH:mm' }}</td>
                }
                <td>{{ invoice.clientName }}</td>
                @if (showSeller) {
                  <td><app-user-avatar [user]="invoice.sale.seller" size="xs" [showName]="true" /></td>
                }
                <td class="num">{{ invoice.itemCount }}</td>
                <td class="num">{{ invoice.sale.totalAmount | ariary }}</td>
                <td><app-payment-status-badge [status]="invoice.paymentStatus" /></td>
                <td>
                  <app-delivery-status-badge [status]="invoice.deliveryStatus" />
                  <app-handler-chip [invoice]="invoice" />
                </td>
                @if (showDetail) {
                  <td class="col-detail">
                    <button
                      class="lnk-detail"
                      type="button"
                      [attr.aria-label]="'Articles de la facture ' + invoice.invoiceNumber"
                      (click)="openDetail(invoice)"
                    >
                      Détail
                    </button>
                    <!-- What the detail offers, without opening it: print, cash in, serve… -->
                    <app-action-menu
                      [actions]="menuOf(invoice)"
                      [label]="'Actions sur la facture ' + invoice.invoiceNumber"
                      (picked)="pick(invoice, $event)"
                    />
                  </td>
                }
                @if (actionLabel) {
                  <td>
                    @if (actionLabel(invoice); as label) {
                      <button
                        class="btn small"
                        type="button"
                        [disabled]="busy"
                        (click)="action.emit(invoice)"
                      >
                        {{ label }}
                      </button>
                    } @else {
                      —
                    }
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>

      <!-- Same rows, phone layout. Hidden by CSS above the breakpoint. -->
      <ul class="inv-cards">
        @for (invoice of invoices; track invoice.id) {
          <li class="inv-card">
            <div class="head">
              <strong>{{ invoice.invoiceNumber }}</strong>
              @if (showDate) {
                <span class="muted">{{ invoice.invoiceDate | date: 'dd/MM HH:mm' }}</span>
              }
            </div>
            <div class="who">
              {{ invoice.clientName }}
              @if (showSeller) {
                <span class="muted">—</span>
                <app-user-avatar [user]="invoice.sale.seller" size="xs" [showName]="true" />
              }
            </div>
            <div class="badges">
              <app-payment-status-badge [status]="invoice.paymentStatus" />
              <app-delivery-status-badge [status]="invoice.deliveryStatus" />
              <app-handler-chip [invoice]="invoice" />
              <span class="muted">{{ invoice.itemCount }} article(s)</span>
            </div>
            <div class="foot">
              <span class="amount">{{ invoice.sale.totalAmount | ariary }}</span>
              @if (actionLabel && actionLabel(invoice); as label) {
                <button
                  class="btn small"
                  type="button"
                  [disabled]="busy"
                  (click)="action.emit(invoice)"
                >
                  {{ label }}
                </button>
              }
            </div>
            @if (showDetail) {
              <div class="card-links">
                <button class="lnk-detail" type="button" (click)="openDetail(invoice)">
                  Voir les articles ›
                </button>
                <app-action-menu
                  [actions]="menuOf(invoice)"
                  [label]="'Actions sur la facture ' + invoice.invoiceNumber"
                  (picked)="pick(invoice, $event)"
                />
              </div>
            }
          </li>
        }
      </ul>

      @if (detail(); as invoice) {
        <app-invoice-detail-flow
          [invoice]="invoice"
          [showSeller]="showSeller"
          [allowCancel]="allowCancel"
          [start]="startWith()"
          (changed)="changed.emit()"
          (closed)="closeDetail()"
        />
      }
    } @else if (showsEmpty(state)) {
      <div class="empty">{{ emptyMessage }}</div>
    }
  `,
})
export class InvoiceTableComponent {
  @Input({ required: true }) invoices: readonly Invoice[] = [];
  @Input() showSeller = true;
  @Input() showDate = false;
  /** The list's loading state: "Chargement…" or the failure in place of the empty message. */
  @Input() state?: LoadState;
  protected readonly showsEmpty = showsEmpty;
  @Input() emptyMessage = 'Aucune facture pour le moment.';
  /** Blocks the row actions while a call is in flight, so nothing is posted twice. */
  @Input() busy = false;
  /** Returns the button label for a row, or null to show no action. */
  @Input() actionLabel?: (invoice: Invoice) => string | null;
  /** Lets a row open its articles in a dialog. On by default: every screen benefits from it. */
  @Input() showDetail = true;
  /**
   * Offers "Annuler la commande" in the detail dialog, on the orders this account may cancel.
   * Off by default: the depot and stock screens hand goods over, they do not undo orders.
   */
  @Input() allowCancel = false;
  @Output() readonly action = new EventEmitter<Invoice>();
  /**
   * An order moved from its detail — paid, handed over, its cash remitted, cancelled. A list read
   * from the InvoiceStore follows by itself; this is for the figures a screen computes elsewhere.
   */
  @Output() readonly changed = new EventEmitter<void>();

  /**
   * The invoice whose articles are on screen, kept by id rather than by reference. The detail
   * closes by itself when the list, kept current by the store, no longer holds the order.
   */
  private readonly detailId = signal<number | null>(null);

  /** Read back from the current list, so a reload refreshes the open dialog. */
  protected detail(): Invoice | null {
    const id = this.detailId();
    return id === null ? null : (this.invoices.find((invoice) => invoice.id === id) ?? null);
  }

  /** The action picked in a line's "⋮", run without the detail; null opens the detail. */
  protected readonly startWith = signal<InvoiceActionKind | null>(null);
  private readonly auth = inject(AuthService);

  protected openDetail(invoice: Invoice): void {
    this.startWith.set(null);
    this.detailId.set(invoice.id);
  }

  protected closeDetail(): void {
    this.detailId.set(null);
    this.startWith.set(null);
  }

  /** The detail's own actions for this person, cancelling only where this list allows it. */
  protected menuOf(invoice: Invoice): InvoiceMenuEntry[] {
    return invoiceMenu(
      availableActions(invoice, invoiceActionContext(this.auth)).filter(
        (action) => action.kind !== InvoiceActionKind.CANCEL || this.allowCancel,
      ),
    );
  }

  protected pick(invoice: Invoice, id: InvoiceMenuId): void {
    if (id === SHOW_DETAIL) {
      this.openDetail(invoice);
      return;
    }
    this.startWith.set(id);
    this.detailId.set(invoice.id);
  }
}
