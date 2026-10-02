import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { CANCEL_REASON_LABELS, CashTrail, Invoice, UserRef } from '../../core/models';
import { InvoiceAction } from '../../core/orders/invoice-actions';
import { InvoiceLinesComponent } from './invoice-lines.component';
import { DeliveryStatusBadgeComponent, PaymentStatusBadgeComponent } from './status-badges.component';
import { HandlerChipComponent } from './handler-chip.component';
import { UserAvatarComponent } from './user-avatar.component';

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
    UserAvatarComponent,
    HandlerChipComponent,
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
                — vendu par
                <app-user-avatar [user]="invoice.sale.seller" size="xs" [showName]="true" />
              }
            </div>
            <div class="badges">
              <app-payment-status-badge [status]="invoice.paymentStatus" />
              <app-delivery-status-badge [status]="invoice.deliveryStatus" />
              <app-handler-chip [invoice]="invoice" />
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          @if (notice) {
            <p class="notice" role="status">✓ {{ notice }}</p>
          }
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
          @if (invoice.cashTrail; as cash) {
            <p class="cash-trail">
              @if (cash.remittanceId === null) {
                Argent encaissé, détenu par
                <app-user-avatar [user]="holderOf(cash)" size="xs" />
                <strong>{{ cash.holderName }}</strong> — à remettre à
                la caisse.
              } @else {
                Argent versé par
                <app-user-avatar [user]="holderOf(cash)" size="xs" />
                <strong>{{ cash.holderName }}</strong> (versement V-{{
                  cash.remittanceId
                }}) — à confirmer par la caisse.
              }
            </p>
          }
          <app-invoice-lines [invoice]="invoice" />
        </div>

        <div class="modal-foot">
          @if (pending(); as action) {
            <span class="confirm-question">{{ action.confirm }}</span>
            <button class="btn ghost" type="button" [disabled]="busy" (click)="pending.set(null)">
              Non
            </button>
            <button class="btn" type="button" [disabled]="busy" (click)="press(action, true)">
              Oui
            </button>
          } @else {
            <!-- What undoes the order sits apart on the left, away from the step it waits for. -->
            @for (action of leftFirst(); track action.kind) {
              <button
                type="button"
                [class]="classOf(action)"
                [disabled]="busy"
                (click)="press(action)"
              >
                {{ action.label }}
              </button>
            }
            <button class="btn ghost" type="button" (click)="closed.emit()">Fermer</button>
          }
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

    .notice {
      margin: 0 0 12px;
      padding: 10px 12px;
      border-radius: 8px;
      background: var(--brand-soft);
      color: var(--brand-dark);
      font-weight: 600;
    }

    .cash-trail {
      margin: 0 0 12px;
      padding: 10px 12px;
      border-radius: 8px;
      background: var(--amber-soft);
      color: var(--amber);
    }

    .modal-foot {
      flex-wrap: wrap;
    }

    .danger-text {
      color: var(--red);
      margin-right: auto;
    }

    .confirm-question {
      margin-right: auto;
      font-weight: 600;
    }
  `,
})
export class InvoiceDetailDialogComponent {
  @Input({ required: true }) invoice!: Invoice;
  /** The cash-desk screens show one seller's own invoices; naming them there says nothing. */
  @Input() showSeller = true;
  /** A line above the detail, e.g. "Vente enregistrée" when it opens right after the sale. */
  @Input() notice: string | null = null;
  /**
   * The buttons for this order and this person — decided by the list, from the rules of
   * `availableActions`; this dialog only shows them and says which was pressed.
   */
  @Input() actions: readonly InvoiceAction[] = [];
  /** An action is under way: every button waits. */
  @Input() busy = false;
  @Output() readonly closed = new EventEmitter<void>();
  @Output() readonly act = new EventEmitter<InvoiceAction>();

  protected readonly reasonLabels = CANCEL_REASON_LABELS;

  /** The action waiting for its "Oui", when it asks one. */
  protected readonly pending = signal<InvoiceAction | null>(null);

  protected press(action: InvoiceAction, confirmed = false): void {
    if (action.confirm && !confirmed) {
      this.pending.set(action);
      return;
    }
    this.pending.set(null);
    this.act.emit(action);
  }

  /** Who holds the order's cash, as the avatar takes a person. */
  protected holderOf(cash: CashTrail): UserRef {
    return { id: cash.holderId, fullName: cash.holderName, photoVersion: cash.holderPhotoVersion };
  }

  protected leftFirst(): InvoiceAction[] {
    return [...this.actions].sort((a, b) => Number(b.tone === 'danger') - Number(a.tone === 'danger'));
  }

  protected classOf(action: InvoiceAction): string {
    switch (action.tone) {
      case 'primary':
        return 'btn';
      case 'danger':
        return 'btn ghost danger-text';
      default:
        return 'btn ghost';
    }
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
