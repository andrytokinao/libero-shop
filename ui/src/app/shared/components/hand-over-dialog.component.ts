import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Invoice } from '../../core/models';
import { AriaryPipe } from '../pipes/ariary.pipe';

/**
 * Handing an unpaid order over: does the customer pay now, to whoever serves, or later at the
 * till? Both are allowed — the shop decides nothing here, the customer does.
 */
@Component({
  selector: 'app-hand-over-dialog',
  standalone: true,
  imports: [AriaryPipe],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="hand-over-title">
        <div class="modal-head">
          <div>
            <h2 id="hand-over-title">Remettre {{ invoice.invoiceNumber }}</h2>
            <div class="sub muted">{{ invoice.clientName }} — non payée</div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>
        <div class="modal-body">
          <div class="amount">{{ invoice.sale.totalAmount | ariary }}</div>
          <p class="muted" style="margin:0; text-align:center;">Le client paie…</p>
        </div>
        <div class="modal-foot send-actions">
          <button class="btn ghost" type="button" [disabled]="busy" (click)="chosen.emit(false)">
            Plus tard, à la caisse
          </button>
          <button class="btn" type="button" [disabled]="busy" (click)="chosen.emit(true)">
            Maintenant — encaisser et remettre
          </button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .amount {
      font-size: 28px;
      font-weight: 700;
      text-align: center;
      margin-bottom: 6px;
    }
  `,
})
export class HandOverDialogComponent {
  @Input({ required: true }) invoice!: Invoice;
  @Input() busy = false;
  /** true: the cash is taken now; false: served, the bill is left to the till. */
  @Output() readonly chosen = new EventEmitter<boolean>();
  @Output() readonly closed = new EventEmitter<void>();

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
