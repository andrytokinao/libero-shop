import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StockApi } from '../../core/api/stock.api';
import { ADJUSTMENT_REASONS, AdjustmentReason, Product } from '../../core/models';
import { formatAmount, formatQuantity, roundQuantity } from '../../core/sale/sale-unit';
import { ToastService } from '../../core/services/toast.service';

/**
 * Corrects one product's stock, from its menu: breakage, loss, or the shelf counted.
 *
 * <p>The form of the "Corrections de stock" screen with the product already chosen. One quantity
 * field read two ways — what is on the shelf for an inventory, what was lost otherwise — and the
 * label and the sentence under it say which, so "37" is never typed meaning the other.
 */
@Component({
  selector: 'app-stock-adjust-dialog',
  standalone: true,
  imports: [FormsModule],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <form class="modal" role="dialog" aria-modal="true" aria-labelledby="sa-title" (ngSubmit)="register()">
        <div class="modal-head">
          <div>
            <h2 id="sa-title">Corriger le stock — {{ product().name }}</h2>
            <div class="sub muted">
              En stock : {{ formatAmount(product().stockQuantity, product().unit) }}. La correction
              est gardée dans l'historique, avec son motif.
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          <div class="fld">
            <label for="sa-reason">Motif</label>
            <select
              id="sa-reason"
              name="reason"
              class="field"
              [ngModel]="reason()"
              (ngModelChange)="reason.set($event)"
            >
              @for (choice of reasons; track choice.value) {
                <option [ngValue]="choice.value">{{ choice.label }}</option>
              }
            </select>
          </div>
          <div class="fld">
            <label for="sa-qty">
              {{ isCount() ? 'Quantité comptée' : 'Quantité perdue' }} ({{ product().unit ?? 'unité' }})
            </label>
            <input
              id="sa-qty"
              name="quantity"
              type="text"
              inputmode="decimal"
              autocomplete="off"
              [ngModel]="quantity()"
              (ngModelChange)="quantity.set($event)"
            />
          </div>
          <div class="fld">
            <label for="sa-comment">
              Commentaire {{ reason() === 'OTHER' ? '(obligatoire)' : '(facultatif)' }}
            </label>
            <input
              id="sa-comment"
              name="comment"
              type="text"
              autocomplete="off"
              maxlength="255"
              [ngModel]="comment()"
              (ngModelChange)="comment.set($event)"
            />
          </div>
          <!-- Said before it is done: a correction has no receipt or ticket to check it against. -->
          @if (preview(); as line) {
            <div class="muted" [class.sa-refused]="line.refused" style="font-size:12.5px;">{{ line.text }}</div>
          }
        </div>

        <div class="modal-foot">
          <button class="btn ghost" type="button" (click)="closed.emit()">Annuler</button>
          <button class="btn" type="submit" [disabled]="!canSubmit()">
            {{ busy() ? 'Enregistrement...' : 'Corriger le stock' }}
          </button>
        </div>
      </form>
    </div>
  `,
  styles: `
    .sa-refused {
      color: var(--red);
    }
  `,
})
export class StockAdjustDialogComponent implements OnInit {
  private readonly api = inject(StockApi);
  private readonly toasts = inject(ToastService);

  readonly product = input.required<Product>();
  /** "Compter ce produit" opens on the count, "Corriger le stock" on a loss. */
  readonly initialReason = input<AdjustmentReason>('INVENTORY_COUNT');
  readonly changed = output<void>();
  readonly closed = output<void>();

  protected readonly reasons = ADJUSTMENT_REASONS;

  protected readonly reason = signal<AdjustmentReason>('INVENTORY_COUNT');
  /** As typed: a string, because an empty field is not zero — and zero is a valid count. */
  protected readonly quantity = signal('');
  protected readonly comment = signal('');
  protected readonly busy = signal(false);

  protected readonly isCount = computed(() => this.reason() === 'INVENTORY_COUNT');

  /** Here rather than in a field: the inputs are not there yet before this. */
  ngOnInit(): void {
    this.reason.set(this.initialReason());
  }

  /** Zero included, unlike a quantity sold: a shelf may be counted empty. */
  private readonly quantityNumber = computed<number | null>(() => {
    const text = this.quantity().replace(/\s/g, '').replace(',', '.');
    return /^\d+(\.\d{1,3})?$/.test(text) ? Number(text) : null;
  });

  /** What the stock would move by, from the figure this screen holds; the server decides. */
  private readonly difference = computed<number | null>(() => {
    const quantity = this.quantityNumber();
    if (quantity === null) {
      return null;
    }
    return roundQuantity(this.isCount() ? quantity - this.product().stockQuantity : -quantity);
  });

  protected readonly preview = computed<{ text: string; refused: boolean } | null>(() => {
    const product = this.product();
    const difference = this.difference();
    if (difference === null) {
      return null;
    }
    const unit = product.unit;
    if (difference === 0) {
      return {
        text: this.isCount()
          ? `Le stock est déjà de ${formatAmount(product.stockQuantity, unit)} : rien à corriger.`
          : 'La quantité perdue doit être supérieure à zéro.',
        refused: true,
      };
    }
    const after = roundQuantity(product.stockQuantity + difference);
    if (after < 0) {
      return {
        text: `Le stock n'est que de ${formatAmount(product.stockQuantity, unit)}. Si le rayon a été compté, choisissez « Inventaire ».`,
        refused: true,
      };
    }
    const sign = difference > 0 ? '+' : '−';
    return {
      text: `Stock : ${formatAmount(product.stockQuantity, unit)} → ${formatAmount(after, unit)} (${sign}${formatQuantity(Math.abs(difference))}).`,
      refused: false,
    };
  });

  protected readonly formatAmount = formatAmount;

  protected canSubmit(): boolean {
    const preview = this.preview();
    return (
      !this.busy() &&
      preview !== null &&
      !preview.refused &&
      (this.reason() !== 'OTHER' || this.comment().trim() !== '')
    );
  }

  protected register(): void {
    const quantity = this.quantityNumber();
    if (!this.canSubmit() || quantity === null) {
      return;
    }
    this.busy.set(true);
    this.api
      .registerAdjustment({
        productId: this.product().id,
        reason: this.reason(),
        quantity,
        comment: this.comment().trim() || null,
      })
      .subscribe({
        next: (adjustment) => {
          const sign = adjustment.quantity > 0 ? '+' : '−';
          this.toasts.show(
            `Stock de ${adjustment.product.name} corrigé : ` +
              `${sign}${formatAmount(Math.abs(adjustment.quantity), adjustment.product.unit)}.`,
          );
          this.changed.emit();
          this.closed.emit();
        },
        error: () => this.busy.set(false),
      });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
