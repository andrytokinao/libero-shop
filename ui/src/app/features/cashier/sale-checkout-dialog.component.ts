import { Component, ElementRef, afterNextRender, computed, inject, input, model, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PAYMENT_METHOD_LABELS, PaymentMethod, PaymentStatus } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/** How the customer settles, as the checkout dialog hands it back. */
export interface SaleCheckout {
  readonly paymentStatus: PaymentStatus;
  readonly paymentMethod: PaymentMethod;
}

/**
 * The last questions before a sale is recorded: for whom, and how it is paid.
 *
 * <p>Out of the basket's way: the till is about products until the customer is ready to pay,
 * and only then asks the rest. Worked from the keyboard like the till — the client field has the
 * focus, Enter or F10 records, Escape goes back to the basket untouched.
 *
 * <p>The client's name is a two-way `model`: the till keeps it, so a sale put on hold and
 * resumed comes back with its name.
 */
@Component({
  selector: 'app-sale-checkout-dialog',
  standalone: true,
  imports: [FormsModule, AriaryPipe],
  host: {
    '(document:keydown.escape)': 'cancel()',
    '(document:keydown.f10)': 'submitFromKey($event)',
  },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <form
        class="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="checkout-title"
        (ngSubmit)="submit()"
      >
        <div class="modal-head">
          <div>
            <h2 id="checkout-title">Encaisser la vente</h2>
            <div class="sub muted">{{ units() }} article(s)</div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="cancel()">✕</button>
        </div>

        <div class="modal-body">
          <div class="amount">{{ total() | ariary }}</div>

          <div class="fld">
            <label for="checkout-client">{{ auth.words().client }}</label>
            <input
              #client
              id="checkout-client"
              name="client"
              type="text"
              autocomplete="off"
              [placeholder]="auth.words().clientPlaceholder"
              [ngModel]="clientName()"
              (ngModelChange)="clientName.set($event)"
            />
          </div>

          @if (mayLeaveUnpaid()) {
            <div class="fld">
              <label for="checkout-status">Statut du paiement</label>
              <select
                id="checkout-status"
                name="status"
                class="field"
                [ngModel]="chosenStatus()"
                (ngModelChange)="chosenStatus.set($event)"
              >
                <option [ngValue]="PaymentStatus.PAID">Payée à la caisse</option>
                <option [ngValue]="PaymentStatus.UNPAID">
                  Non payée (à régler à la remise — {{ auth.words().depot.toLowerCase() }})
                </option>
              </select>
            </div>
          }

          @if (paymentStatus() === PaymentStatus.PAID) {
            <div class="fld">
              <span class="label">Mode de paiement</span>
              <div class="methods" role="radiogroup" aria-label="Mode de paiement">
                @for (method of methods; track method) {
                  <button
                    type="button"
                    role="radio"
                    class="btn"
                    [class.ghost]="paymentMethod() !== method"
                    [attr.aria-checked]="paymentMethod() === method"
                    (click)="paymentMethod.set(method)"
                  >
                    {{ labels[method] }}
                  </button>
                }
              </div>
            </div>
          }
        </div>

        <div class="modal-foot">
          <button class="btn ghost" type="button" [disabled]="busy()" (click)="cancel()">
            Retour au panier
          </button>
          <button class="btn" type="submit" [disabled]="busy()">
            {{ busy() ? 'Enregistrement…' : 'Enregistrer la vente' }}
            <span class="key-hint">(F10)</span>
          </button>
        </div>
      </form>
    </div>
  `,
  styles: `
    .amount {
      font-size: 30px;
      font-weight: 700;
      text-align: center;
      margin: 4px 0 18px;
    }

    .fld {
      margin-bottom: 14px;
    }

    .label {
      display: block;
      font-size: 12px;
      font-weight: 600;
      margin-bottom: 6px;
    }

    .methods {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
      gap: 8px;
    }

    @media (max-width: 980px) {
      .key-hint {
        display: none;
      }
    }
  `,
})
export class SaleCheckoutDialogComponent {
  protected readonly auth = inject(AuthService);

  readonly total = input.required<number>();
  readonly units = input.required<number>();
  readonly busy = input(false);
  /** For whom — kept by the till, see the class comment. */
  readonly clientName = model('');

  readonly confirmed = output<SaleCheckout>();
  readonly closed = output<void>();

  protected readonly PaymentStatus = PaymentStatus;
  protected readonly methods = Object.values(PaymentMethod);
  protected readonly labels = PAYMENT_METHOD_LABELS;

  /**
   * A till leaves a sale unpaid only for the depot to collect on hand-over. With no money taken
   * there, the choice is not offered: what the till sells, it is paid for. (Order takers still
   * send unpaid orders, settled at the till under "À encaisser".)
   */
  protected readonly mayLeaveUnpaid = computed(() => this.auth.settings().payAtDepot);
  protected readonly chosenStatus = signal<PaymentStatus>(PaymentStatus.PAID);
  /** What the sale is sent as: the pick, or paid when there is nothing to pick. */
  protected readonly paymentStatus = computed(() =>
    this.mayLeaveUnpaid() ? this.chosenStatus() : PaymentStatus.PAID,
  );
  protected readonly paymentMethod = signal<PaymentMethod>(PaymentMethod.CASH);

  private readonly client = viewChild.required<ElementRef<HTMLInputElement>>('client');

  constructor() {
    afterNextRender(() => this.client().nativeElement.focus());
  }

  protected submit(): void {
    if (!this.busy()) {
      this.confirmed.emit({ paymentStatus: this.paymentStatus(), paymentMethod: this.paymentMethod() });
    }
  }

  /** F10 records here as on the till, and is kept from the browser's menu bar. */
  protected submitFromKey(event: Event): void {
    event.preventDefault();
    this.submit();
  }

  protected cancel(): void {
    if (!this.busy()) {
      this.closed.emit();
    }
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.cancel();
    }
  }
}
