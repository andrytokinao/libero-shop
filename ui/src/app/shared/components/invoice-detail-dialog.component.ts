import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CANCEL_REASON_LABELS, Invoice } from '../../core/models';
import { InvoiceLinesComponent } from './invoice-lines.component';
import { DeliveryStatusBadgeComponent, PaymentStatusBadgeComponent } from './status-badges.component';

/**
 * The articles sold on one invoice, in a dialog.
 *
 * <p>The picking list is read while the goods are counted, so it gets the screen to itself
 * instead of a fold inside the row: the storekeeper reads one list at a time, and the same
 * dialog works on a phone, where an unfolded table row had nowhere to go.
 *
 * <p>Built on the shell's own dialog classes rather than a component library, like the
 * licence dialog — the header repeats who the invoice is for and where it stands, because
 * the dialog covers the row it was opened from.
 */
@Component({
  selector: 'app-invoice-detail-dialog',
  standalone: true,
  imports: [
    DatePipe,
    InvoiceLinesComponent,
    PaymentStatusBadgeComponent,
    DeliveryStatusBadgeComponent,
  ],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal wide" role="dialog" aria-modal="true" aria-labelledby="inv-detail-title">
        <div class="modal-head">
          <div>
            <h2 id="inv-detail-title">Facture {{ invoice.invoiceNumber }}</h2>
            <div class="sub muted">
              {{ invoice.clientName }} — {{ invoice.invoiceDate | date: 'dd/MM/y HH:mm' }}
              @if (showSeller) {
                — vendu par {{ invoice.sale.seller.fullName }}
              }
            </div>
            <div class="badges">
              <app-payment-status-badge [status]="invoice.paymentStatus" />
              <app-delivery-status-badge [status]="invoice.deliveryStatus" />
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          @if (invoice.cancellation; as c) {
            <p class="cancelled">
              Annulée le {{ c.at | date: 'dd/MM/y HH:mm' }}
              @if (c.byName) {
                par {{ c.byName }}
              }
              — {{ reasonLabels[c.reason] }}
              @if (c.comment) {
                <br />« {{ c.comment }} »
              }
            </p>
          }
          <app-invoice-lines [invoice]="invoice" />
        </div>

        <div class="modal-foot">
          @if (canCancel) {
            <button class="btn ghost danger-text" type="button" (click)="cancelRequested.emit()">
              Annuler la commande
            </button>
          }
          <button class="btn ghost" type="button" (click)="closed.emit()">Fermer</button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .cancelled {
      margin: 0 0 12px;
      padding: 10px 12px;
      border-radius: 8px;
      background: var(--red-soft);
      color: var(--red);
    }

    .danger-text {
      color: var(--red);
      margin-right: auto;
    }
  `,
})
export class InvoiceDetailDialogComponent {
  @Input({ required: true }) invoice!: Invoice;
  /** The cash-desk screens show one seller's own invoices; naming them there says nothing. */
  @Input() showSeller = true;
  /** Offers "Annuler la commande"; the list decides, knowing who is looking and the settings. */
  @Input() canCancel = false;
  @Output() readonly closed = new EventEmitter<void>();
  @Output() readonly cancelRequested = new EventEmitter<void>();

  protected readonly reasonLabels = CANCEL_REASON_LABELS;

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
