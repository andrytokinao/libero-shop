import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { RemittanceApi } from '../../core/api/remittance.api';
import { RemittanceStore } from '../../core/store/remittance.store';
import { Payment } from '../../core/models';
import {
  RemitQuestion,
  confirmsOwnSlip,
  describeSlip,
  remitLabel,
  remitQuestion,
} from '../../core/orders/remittance';
import { reloadOnOrderChange } from '../../core/realtime/reload-on';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog.component';
import { InvoiceLinkComponent } from '../../shared/components/invoice-link.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/**
 * The cash a storekeeper has collected on hand-over and not yet brought to the desk, with the
 * two ways of bringing it: one order at a time, or everything at once.
 *
 * <p>Shown under the queue of orders to hand over, so the button appears where the order just
 * left — "Encaisser et remettre" moves an order from the list above to this one — and on the
 * depot cash page. Nothing is paid until a cashier confirms counting it; the toast says so.
 */
@Component({
  selector: 'app-cash-in-hand',
  standalone: true,
  imports: [DatePipe, KpiCardComponent, AriaryPipe, InvoiceLinkComponent, ConfirmDialogComponent],
  template: `
    <div class="card">
      <h2>
        Argent à remettre à la caisse
        <small>espèces encaissées sur des commandes, à confirmer par le caissier</small>
      </h2>
      <app-kpi-card
        [flat]="true"
        label="Espèces en main"
        [value]="total() | ariary"
        [hint]="payments().length + ' commande(s)'"
      />

      @if (payments().length) {
        <div class="table-scroll">
          <table class="inv-table" style="margin-top:14px;">
            <thead>
              <tr>
                <th>N° facture</th>
                <th>Client</th>
                <th>Encaissée le</th>
                <th class="num">Montant</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (payment of payments(); track payment.id) {
                <tr>
                  <td><app-invoice-link [invoiceId]="payment.invoice.id" [number]="payment.invoice.invoiceNumber" /></td>
                  <td>{{ payment.invoice.clientName }}</td>
                  <td class="muted">{{ payment.paymentDate | date: 'dd/MM HH:mm' }}</td>
                  <td class="num">{{ payment.amount | ariary }}</td>
                  <td>
                    <button
                      class="btn small"
                      type="button"
                      [disabled]="busy()"
                      (click)="ask([payment], [payment.invoice.id])"
                    >
                      {{ remitLabel() }}
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <!-- Same rows, phone layout. Hidden by CSS above the breakpoint. -->
        <ul class="inv-cards" style="margin-top:14px;">
          @for (payment of payments(); track payment.id) {
            <li class="inv-card">
              <div class="head">
                <strong><app-invoice-link [invoiceId]="payment.invoice.id" [number]="payment.invoice.invoiceNumber" /></strong>
                <span class="muted">{{ payment.paymentDate | date: 'dd/MM HH:mm' }}</span>
              </div>
              <div class="who">{{ payment.invoice.clientName }}</div>
              <div class="foot">
                <span class="amount">{{ payment.amount | ariary }}</span>
                <button
                  class="btn small"
                  type="button"
                  [disabled]="busy()"
                  (click)="ask([payment], [payment.invoice.id])"
                >
                  {{ remitLabel() }}
                </button>
              </div>
            </li>
          }
        </ul>

        @if (payments().length > 1) {
          <button
            class="btn block"
            type="button"
            style="margin-top:14px;"
            [disabled]="busy()"
            (click)="ask(payments(), [])"
          >
            {{ selfConfirming() ? 'Tout mettre en caisse' : 'Tout remettre à la caisse' }} ({{ total() | ariary }})
          </button>
        }
      } @else {
        <div class="empty" style="margin-top:14px;">Aucun argent à remettre pour le moment.</div>
      }

      @if (asking(); as question) {
        <app-confirm-dialog
          [title]="question.title"
          [message]="question.message"
          [confirmLabel]="question.confirmLabel"
          [busy]="busy()"
          (confirmed)="remit(question.invoiceIds)"
          (closed)="asking.set(null)"
        />
      }
    </div>
  `,
})
export class CashInHandComponent {
  private readonly api = inject(RemittanceApi);
  private readonly remittances = inject(RemittanceStore);
  private readonly toasts = inject(ToastService);

  private readonly auth = inject(AuthService);

  protected readonly busy = signal(false);
  /** The question on screen before cash is handed over, with the orders it would send. */
  protected readonly asking = signal<(RemitQuestion & { invoiceIds: number[] }) | null>(null);

  /** A cashier with no second person to count puts the cash straight in the till. */
  protected readonly selfConfirming = computed(() =>
    confirmsOwnSlip(this.auth.roles(), this.auth.settings()),
  );
  protected readonly remitLabel = computed(() => remitLabel(this.selfConfirming()));

  private readonly resource = apiResource<Payment[]>([], () => this.api.cashInHand());
  protected readonly payments = this.resource.value;
  protected readonly total = computed(() =>
    this.payments().reduce((sum, payment) => sum + payment.amount, 0),
  );

  constructor() {
    // A hand-over just made, here or on another screen of the same account, adds a line.
    reloadOnOrderChange(this.resource);
  }

  /** For a parent that has just handed an order over. */
  reload(): void {
    this.resource.reload();
  }

  /**
   * Cash leaving one's hands is said first, amount and orders in full: a tap on the wrong row
   * would otherwise send money that has not been counted out.
   *
   * @param invoiceIds what the server is sent: one order's, or none for everything in hand
   */
  protected ask(payments: readonly Payment[], invoiceIds: number[]): void {
    const amount = payments.reduce((sum, payment) => sum + payment.amount, 0);
    const numbers = payments.map((payment) => payment.invoice.invoiceNumber);
    this.asking.set({ ...remitQuestion(numbers, amount, this.selfConfirming()), invoiceIds });
  }

  /** @param invoiceIds one order's, or none for everything in hand */
  protected remit(invoiceIds: number[]): void {
    this.busy.set(true);
    this.remittances.submit(invoiceIds).subscribe({
      next: (slip) => {
        this.busy.set(false);
        this.asking.set(null);
        this.resource.reload();
        this.toasts.show(describeSlip(slip));
      },
      error: () => {
        this.busy.set(false);
        this.asking.set(null);
        this.resource.reload();
      },
    });
  }
}
