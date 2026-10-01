import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CANCEL_REASON_LABELS,
  CancelInvoiceRequest,
  CancelReason,
  DeliveryStatus,
  Invoice,
} from '../../core/models';
import { AriaryPipe } from '../pipes/ariary.pipe';

/**
 * Cancelling an unpaid order: a reason in one tap, a comment when it is needed, and a sentence
 * that says what will happen to the goods before anything is done.
 *
 * <p>Already handed over, the goods are gone: nothing returns to stock, and the comment — what
 * happened with the customer — is required. The server holds the same rules.
 */
@Component({
  selector: 'app-cancel-order-dialog',
  standalone: true,
  imports: [FormsModule, AriaryPipe],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="cancel-title">
        <div class="modal-head">
          <div>
            <h2 id="cancel-title">Annuler {{ invoice.invoiceNumber }}</h2>
            <div class="sub muted">
              {{ invoice.clientName }} — {{ invoice.sale.totalAmount | ariary }}
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          <p class="effect" [class.warn]="delivered">
            @if (delivered) {
              Commande déjà remise : les articles ne reviennent pas en stock. Notez ce qui s'est
              passé avec le client.
            } @else {
              Les {{ invoice.itemCount }} article(s) reviennent en stock.
            }
          </p>

          <div class="reasons" role="radiogroup" aria-label="Motif">
            @for (r of reasons; track r) {
              <button
                type="button"
                class="reason"
                role="radio"
                [attr.aria-checked]="reason() === r"
                [class.active]="reason() === r"
                (click)="reason.set(r)"
              >
                {{ labels[r] }}
              </button>
            }
          </div>

          <label class="lbl" for="cancel-comment">
            Commentaire {{ commentRequired() ? '(obligatoire)' : '(facultatif)' }}
          </label>
          <textarea
            id="cancel-comment"
            class="field"
            rows="3"
            maxlength="255"
            [placeholder]="delivered ? 'Ex : client parti sans payer' : ''"
            [ngModel]="comment()"
            (ngModelChange)="comment.set($event)"
          ></textarea>
        </div>

        <div class="modal-foot">
          <button class="btn ghost" type="button" (click)="closed.emit()">Retour</button>
          <button class="btn cancel" type="button" [disabled]="busy || !valid()" (click)="submit()">
            {{ busy ? 'Annulation…' : 'Annuler la commande' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .effect {
      margin: 0 0 14px;
      padding: 10px 12px;
      border-radius: 8px;
      background: var(--brand-soft);

      &.warn {
        background: var(--red-soft);
        color: var(--red);
      }
    }

    .reasons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 14px;
    }

    .reason {
      border: 2px solid var(--line);
      background: #fff;
      border-radius: 20px;
      padding: 9px 14px;
      font: inherit;
      color: inherit;
      cursor: pointer;

      &.active {
        border-color: var(--red);
        background: var(--red-soft);
        color: var(--red);
        font-weight: 600;
      }
    }

    .lbl {
      display: block;
      font-size: 11.5px;
      font-weight: 600;
      color: var(--ink-soft);
      margin-bottom: 5px;
    }

    textarea {
      width: 100%;
      font: inherit;
    }

    .btn.cancel {
      background: var(--red);
      border-color: var(--red);
    }
  `,
})
export class CancelOrderDialogComponent {
  @Input({ required: true }) invoice!: Invoice;
  @Input() busy = false;
  @Output() readonly confirmed = new EventEmitter<CancelInvoiceRequest>();
  @Output() readonly closed = new EventEmitter<void>();

  protected readonly reasons = Object.values(CancelReason);
  protected readonly labels = CANCEL_REASON_LABELS;
  protected readonly reason = signal<CancelReason | null>(null);
  protected readonly comment = signal('');

  protected get delivered(): boolean {
    return this.invoice.deliveryStatus === DeliveryStatus.DELIVERED;
  }

  protected readonly commentRequired = computed(
    () => this.reason() === CancelReason.OTHER || this.delivered,
  );

  protected readonly valid = computed(
    () => this.reason() !== null && (!this.commentRequired() || this.comment().trim() !== ''),
  );

  protected submit(): void {
    const reason = this.reason();
    if (this.busy || !this.valid() || reason === null) {
      return;
    }
    this.confirmed.emit({ reason, comment: this.comment().trim() || null });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
