import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { RemittanceApi } from '../../core/api/remittance.api';
import { CashRemittance, Payment } from '../../core/models';
import { ORDERS_TOPIC, reloadOnTopic } from '../../core/realtime/reload-on';
import { ToastService } from '../../core/services/toast.service';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
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
  imports: [DatePipe, KpiCardComponent, AriaryPipe],
  template: `
    <div class="card">
      <h2>
        Argent à remettre à la caisse
        <small>encaissé lors des remises de commandes non payées</small>
      </h2>
      <app-kpi-card
        [flat]="true"
        label="Espèces en main"
        [value]="total() | ariary"
        [hint]="payments().length + ' commande(s)'"
      />

      @if (payments().length) {
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
                <td>{{ payment.invoice.invoiceNumber }}</td>
                <td>{{ payment.invoice.clientName }}</td>
                <td class="muted">{{ payment.paymentDate | date: 'dd/MM HH:mm' }}</td>
                <td class="num">{{ payment.amount | ariary }}</td>
                <td>
                  <button
                    class="btn small"
                    type="button"
                    [disabled]="busy()"
                    (click)="remit([payment.invoice.id])"
                  >
                    Remettre à la caisse
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>

        <!-- Same rows, phone layout. Hidden by CSS above the breakpoint. -->
        <ul class="inv-cards" style="margin-top:14px;">
          @for (payment of payments(); track payment.id) {
            <li class="inv-card">
              <div class="head">
                <strong>{{ payment.invoice.invoiceNumber }}</strong>
                <span class="muted">{{ payment.paymentDate | date: 'dd/MM HH:mm' }}</span>
              </div>
              <div class="who">{{ payment.invoice.clientName }}</div>
              <div class="foot">
                <span class="amount">{{ payment.amount | ariary }}</span>
                <button
                  class="btn small"
                  type="button"
                  [disabled]="busy()"
                  (click)="remit([payment.invoice.id])"
                >
                  Remettre à la caisse
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
            (click)="remit([])"
          >
            Tout remettre à la caisse ({{ total() | ariary }})
          </button>
        }
      } @else {
        <div class="empty" style="margin-top:14px;">Aucun argent à remettre pour le moment.</div>
      }
    </div>
  `,
})
export class CashInHandComponent {
  private readonly api = inject(RemittanceApi);
  private readonly toasts = inject(ToastService);

  /** A slip was created: the parent may have lists of its own to refresh. */
  @Output() readonly remitted = new EventEmitter<CashRemittance>();

  protected readonly busy = signal(false);

  private readonly resource = apiResource<Payment[]>([], () => this.api.cashInHand());
  protected readonly payments = this.resource.value;
  protected readonly total = computed(() =>
    this.payments().reduce((sum, payment) => sum + payment.amount, 0),
  );

  constructor() {
    // A hand-over just made, here or on another screen of the same account, adds a line.
    reloadOnTopic(this.resource, ORDERS_TOPIC);
  }

  /** For a parent that has just handed an order over. */
  reload(): void {
    this.resource.reload();
  }

  /** @param invoiceIds one order's, or none for everything in hand */
  protected remit(invoiceIds: number[]): void {
    this.busy.set(true);
    this.api.submit(invoiceIds).subscribe({
      next: (slip) => {
        this.busy.set(false);
        this.resource.reload();
        this.remitted.emit(slip);
        this.toasts.show(
          `Versement V-${slip.id} de ${slip.amount.toLocaleString('fr-FR')} Ar remis à la caisse — ` +
            `en attente de confirmation par le caissier.`,
        );
      },
      error: () => {
        this.busy.set(false);
        this.resource.reload();
      },
    });
  }
}
