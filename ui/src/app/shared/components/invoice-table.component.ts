import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { Invoice, PaymentMethod } from '../../core/models';
import { asksHowToPay } from '../../core/orders/delivery.util';
import { InvoiceActionRunner } from '../../core/orders/invoice-action-runner.service';
import {
  InvoiceAction,
  InvoiceActionKind,
  availableActions,
} from '../../core/orders/invoice-actions';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { AriaryPipe } from '../pipes/ariary.pipe';
import { CancelOrderDialogComponent } from './cancel-order-dialog.component';
import { HandOverDialogComponent } from './hand-over-dialog.component';
import { InvoiceDetailDialogComponent } from './invoice-detail-dialog.component';
import { PayDialogComponent } from './pay-dialog.component';
import { DeliveryStatusBadgeComponent, PaymentStatusBadgeComponent } from './status-badges.component';

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
    DatePipe,
    AriaryPipe,
    PaymentStatusBadgeComponent,
    DeliveryStatusBadgeComponent,
    InvoiceDetailDialogComponent,
    CancelOrderDialogComponent,
    PayDialogComponent,
    HandOverDialogComponent,
  ],
  template: `
    @if (invoices.length) {
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
                <td>{{ invoice.sale.seller.fullName }}</td>
              }
              <td class="num">{{ invoice.itemCount }}</td>
              <td class="num">{{ invoice.sale.totalAmount | ariary }}</td>
              <td><app-payment-status-badge [status]="invoice.paymentStatus" /></td>
              <td><app-delivery-status-badge [status]="invoice.deliveryStatus" /></td>
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
                <span class="muted">— {{ invoice.sale.seller.fullName }}</span>
              }
            </div>
            <div class="badges">
              <app-payment-status-badge [status]="invoice.paymentStatus" />
              <app-delivery-status-badge [status]="invoice.deliveryStatus" />
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
              <button class="lnk-detail" type="button" (click)="openDetail(invoice)">
                Voir les articles ›
              </button>
            }
          </li>
        }
      </ul>

      @if (detail(); as invoice) {
        @switch (step()) {
          @case ('cancel') {
            <app-cancel-order-dialog
              [invoice]="invoice"
              [busy]="running()"
              (confirmed)="run(runner.cancel(invoice, $event))"
              (closed)="step.set(null)"
            />
          }
          @case ('pay') {
            <app-pay-dialog
              [invoice]="invoice"
              [busy]="running()"
              (paid)="run(runner.pay(invoice, $event))"
              (closed)="step.set(null)"
            />
          }
          @case ('collect') {
            <app-pay-dialog
              [invoice]="invoice"
              [busy]="running()"
              [methods]="cashOnly"
              (paid)="run(runner.collect(invoice))"
              (closed)="step.set(null)"
            />
          }
          @case ('handOver') {
            <app-hand-over-dialog
              [invoice]="invoice"
              [busy]="running()"
              (chosen)="run(runner.handOver(invoice, $event))"
              (closed)="step.set(null)"
            />
          }
          @default {
            <app-invoice-detail-dialog
              [invoice]="invoice"
              [showSeller]="showSeller"
              [actions]="actionsFor(invoice)"
              [busy]="running()"
              (act)="act(invoice, $event)"
              (closed)="closeDetail()"
            />
          }
        }
      }
    } @else {
      <div class="empty">{{ emptyMessage }}</div>
    }
  `,
})
export class InvoiceTableComponent {
  @Input({ required: true }) invoices: readonly Invoice[] = [];
  @Input() showSeller = true;
  @Input() showDate = false;
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

  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);
  protected readonly runner = inject(InvoiceActionRunner);

  /** The dialog an action opens over the detail to ask what it needs, or null for the detail. */
  protected readonly step = signal<'pay' | 'collect' | 'handOver' | 'cancel' | null>(null);
  protected readonly running = signal(false);
  protected readonly cashOnly = [PaymentMethod.CASH];

  /** The detail's buttons for this person — see `availableActions` for the rules. */
  protected actionsFor(invoice: Invoice): InvoiceAction[] {
    const settings = this.auth.settings();
    return availableActions(invoice, {
      userId: this.auth.currentUser()?.id ?? null,
      roles: this.auth.roles(),
      features: settings,
      handOverLabel: this.auth.words().handOverAction,
    }).filter((action) => action.kind !== InvoiceActionKind.CANCEL || this.allowCancel);
  }

  /** A button of the detail: straight to the server, or through the dialog that asks first. */
  protected act(invoice: Invoice, action: InvoiceAction): void {
    switch (action.kind) {
      case InvoiceActionKind.PAY:
        return this.step.set('pay');
      case InvoiceActionKind.COLLECT:
        return this.step.set('collect');
      case InvoiceActionKind.CANCEL:
        return this.step.set('cancel');
      case InvoiceActionKind.HAND_OVER:
        return asksHowToPay(invoice, this.auth.settings().payAtDepot)
          ? this.step.set('handOver')
          : this.run(this.runner.handOver(invoice, true));
      case InvoiceActionKind.REMIT_CASH:
        return this.run(this.runner.remitCash(invoice));
      case InvoiceActionKind.CONFIRM_REMITTANCE:
        return this.run(this.runner.confirmRemittance(invoice));
    }
  }

  /**
   * Runs one action and says what happened. The detail stays open on the order, now in its new
   * state — or closes by itself when the list, kept current by the store, no longer holds it. A refusal is told by the error interceptor; the person stays where they were.
   */
  protected run(work: Observable<string>): void {
    this.running.set(true);
    work.subscribe({
      next: (message) => {
        this.running.set(false);
        this.step.set(null);
        this.toasts.show(message);
        this.changed.emit();
      },
      error: () => this.running.set(false),
    });
  }

  /** The invoice whose articles are on screen, kept by id rather than by reference. */
  private readonly detailId = signal<number | null>(null);

  /** Read back from the current list, so a reload refreshes the open dialog. */
  protected detail(): Invoice | null {
    const id = this.detailId();
    return id === null ? null : (this.invoices.find((invoice) => invoice.id === id) ?? null);
  }

  protected openDetail(invoice: Invoice): void {
    this.detailId.set(invoice.id);
  }

  protected closeDetail(): void {
    this.detailId.set(null);
    this.step.set(null);
  }
}
