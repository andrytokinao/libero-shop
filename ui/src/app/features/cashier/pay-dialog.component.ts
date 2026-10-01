import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Invoice, PAYMENT_METHOD_LABELS, PaymentMethod } from '../../core/models';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/**
 * Settling an order at the till: the amount in large, then one button per way of paying.
 * One tap pays — the customer is standing at the counter, and a second step would only slow
 * the queue.
 */
@Component({
  selector: 'app-pay-dialog',
  standalone: true,
  imports: [AriaryPipe],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="pay-title">
        <div class="modal-head">
          <div>
            <h2 id="pay-title">Encaisser {{ invoice.invoiceNumber }}</h2>
            <div class="sub muted">{{ invoice.clientName }}</div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          <div class="amount">{{ invoice.sale.totalAmount | ariary }}</div>
          <div class="methods">
            @for (method of methods; track method) {
              <button
                class="btn"
                type="button"
                [class.ghost]="method !== PaymentMethod.CASH"
                [disabled]="busy"
                (click)="paid.emit(method)"
              >
                {{ labels[method] }}
              </button>
            }
          </div>
        </div>
      </div>
    </div>
  `,
  styles: `
    .amount {
      font-size: 30px;
      font-weight: 700;
      text-align: center;
      margin: 4px 0 18px;
    }

    .methods {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px;

      .btn {
        padding: 16px 10px;
        font-size: 15px;
      }
    }
  `,
})
export class PayDialogComponent {
  @Input({ required: true }) invoice!: Invoice;
  @Input() busy = false;
  /** The ways offered. Cash only for an order taker, whose money is counted at the till later. */
  @Input() methods: readonly PaymentMethod[] = Object.values(PaymentMethod);
  @Output() readonly paid = new EventEmitter<PaymentMethod>();
  @Output() readonly closed = new EventEmitter<void>();

  protected readonly PaymentMethod = PaymentMethod;
  protected readonly labels = PAYMENT_METHOD_LABELS;

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
