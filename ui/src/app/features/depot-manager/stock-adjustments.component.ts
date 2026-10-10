import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { StockApi } from '../../core/api/stock.api';
import { ADJUSTMENT_REASONS, AdjustmentReason, StockAdjustment } from '../../core/models';
import { formatAmount, formatQuantity, parseQuantity, roundQuantity, stockLabel } from '../../core/sale/sale-unit';
import { ToastService } from '../../core/services/toast.service';
import { ProductStore } from '../../core/store/product.store';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/**
 * The stock corrected by hand: breakage, loss, or a shelf counted.
 *
 * <p>One quantity field, read two ways. For an inventory it is what is on the shelf, and the
 * server works out the difference — on its own figure, so a sale rung up while the shelf was
 * being counted is not counted twice. For every other reason it is what was lost. The label and
 * the sentence under the form say which, so "37" is never typed meaning the other.
 */
@Component({
  selector: 'app-stock-adjustments',
  standalone: true,
  imports: [FormsModule, DatePipe, KpiCardComponent, AriaryPipe],
  template: `
    <div class="grid g3">
      <app-kpi-card label="Corrections enregistrées" [value]="adjustments().length" />
      <app-kpi-card label="Unités perdues" [value]="formatQuantity(lostUnits())" />
      <app-kpi-card label="Pertes au prix d'achat" [value]="lostValue() | ariary" />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Corriger le stock
        <small>casse, perte ou inventaire</small>
      </h2>
      <form class="form-row" (ngSubmit)="register()">
        <div class="fld" style="flex:1; min-width:200px;">
          <label for="adjust-product">Produit</label>
          <select
            id="adjust-product"
            name="product"
            class="field"
            [ngModel]="selectedProductId()"
            (ngModelChange)="productId.set($event)"
          >
            @for (product of products(); track product.id) {
              <option [ngValue]="product.id">{{ product.name }} — stock {{ stockLabel(product) }}</option>
            }
          </select>
        </div>
        <div class="fld">
          <label for="adjust-reason">Motif</label>
          <select
            id="adjust-reason"
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
          <label for="adjust-qty">
            {{ isCount() ? 'Quantité comptée' : 'Quantité perdue' }} ({{ baseName() }})
          </label>
          <input
            id="adjust-qty"
            name="quantity"
            type="text"
            inputmode="decimal"
            autocomplete="off"
            style="width:130px;"
            [ngModel]="quantity()"
            (ngModelChange)="quantity.set($event)"
          />
        </div>
        <div class="fld" style="flex:1; min-width:200px;">
          <label for="adjust-comment">
            Commentaire {{ reason() === 'OTHER' ? '(obligatoire)' : '(facultatif)' }}
          </label>
          <input
            id="adjust-comment"
            name="comment"
            type="text"
            autocomplete="off"
            maxlength="255"
            [ngModel]="comment()"
            (ngModelChange)="comment.set($event)"
          />
        </div>
        <button class="btn" type="submit" [disabled]="!canSubmit()">
          {{ busy() ? 'Enregistrement...' : 'Corriger le stock' }}
        </button>
      </form>
      <!-- Said before it is done: a correction has no receipt or ticket to check it against. -->
      @if (preview(); as line) {
        <div class="muted" [class.warn]="line.refused" style="font-size:12.5px;">{{ line.text }}</div>
      }
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Historique des corrections
        <small>un mouvement ADJUSTMENT par correction</small>
      </h2>
      @if (adjustments().length) {
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Produit</th>
              <th>Motif</th>
              <th class="num">Écart</th>
              <th class="num">Valeur d'achat</th>
              <th>Par</th>
              <th>Commentaire</th>
            </tr>
          </thead>
          <tbody>
            @for (adjustment of adjustments(); track adjustment.id) {
              <tr>
                <td class="muted">{{ adjustment.movementDate | date: 'dd/MM/yy HH:mm' }}</td>
                <td>{{ adjustment.product.name }}</td>
                <td>{{ reasonLabel(adjustment.reason) }}</td>
                <td class="num">{{ signed(adjustment) }}</td>
                <td class="num">
                  @if (adjustment.costValue !== null) {
                    {{ adjustment.costValue | ariary }}
                  } @else {
                    <span class="muted">—</span>
                  }
                </td>
                <td>{{ adjustment.performedBy.fullName }}</td>
                <td class="muted">{{ adjustment.comment ?? '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty">Aucune correction de stock pour l'instant.</div>
      }
    </div>
  `,
  styles: `
    .warn {
      color: var(--danger, #c0392b);
    }
  `,
})
export class StockAdjustmentsComponent {
  private readonly api = inject(StockApi);
  private readonly toasts = inject(ToastService);

  protected readonly reasons = ADJUSTMENT_REASONS;

  protected readonly productId = signal<number | null>(null);
  protected readonly reason = signal<AdjustmentReason>('INVENTORY_COUNT');
  /** As typed: a string, because an empty field is not zero — and zero is a valid count. */
  protected readonly quantity = signal('');
  protected readonly comment = signal('');
  protected readonly busy = signal(false);

  // The store keeps each "stock N" current, whoever sells or receives meanwhile.
  private readonly productResource = inject(ProductStore).list();
  private readonly adjustmentResource = apiResource<StockAdjustment[]>([], () => this.api.adjustments());

  protected readonly products = this.productResource.value;
  protected readonly adjustments = this.adjustmentResource.value;

  /** Falls back to the first product until one is picked, so the form is usable at once. */
  protected readonly selectedProductId = computed(
    () => this.productId() ?? this.products()[0]?.id ?? null,
  );
  protected readonly selectedProduct = computed(() =>
    this.products().find((product) => product.id === this.selectedProductId()),
  );
  protected readonly baseName = computed(() => this.selectedProduct()?.unit ?? 'unité');
  protected readonly isCount = computed(() => this.reason() === 'INVENTORY_COUNT');

  protected readonly quantityNumber = computed(() => parseQuantity(this.quantity()));

  /** What the stock would move by, from the figure this screen holds; the server decides. */
  private readonly difference = computed<number | null>(() => {
    const product = this.selectedProduct();
    const quantity = this.quantityNumber();
    if (!product || quantity === null) {
      return null;
    }
    return roundQuantity(this.isCount() ? quantity - product.stockQuantity : -quantity);
  });

  protected readonly preview = computed<{ text: string; refused: boolean } | null>(() => {
    const product = this.selectedProduct();
    const difference = this.difference();
    if (!product || difference === null) {
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
      text: `Stock de ${product.name} : ${formatAmount(product.stockQuantity, unit)} → ${formatAmount(after, unit)} (${sign}${formatQuantity(Math.abs(difference))}).`,
      refused: false,
    };
  });

  /** Losses only: what a count found in excess is not a loss paid back. */
  protected readonly lostUnits = computed(() =>
    roundQuantity(
      this.adjustments().reduce((total, a) => total + (a.quantity < 0 ? -a.quantity : 0), 0),
    ),
  );
  protected readonly lostValue = computed(() =>
    this.adjustments().reduce(
      (total, a) => total + (a.costValue !== null && a.costValue < 0 ? -a.costValue : 0),
      0,
    ),
  );

  protected readonly stockLabel = stockLabel;
  protected readonly formatQuantity = formatQuantity;

  protected reasonLabel(reason: AdjustmentReason): string {
    return ADJUSTMENT_REASONS.find((choice) => choice.value === reason)?.label ?? reason;
  }

  protected signed(adjustment: StockAdjustment): string {
    const sign = adjustment.quantity > 0 ? '+' : '−';
    return `${sign}${formatAmount(Math.abs(adjustment.quantity), adjustment.product.unit)}`;
  }

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
    const productId = this.selectedProductId();
    if (!this.canSubmit() || quantity === null || productId === null) {
      return;
    }
    this.busy.set(true);
    this.api
      .registerAdjustment({
        productId,
        reason: this.reason(),
        quantity,
        comment: this.comment().trim() || null,
      })
      .subscribe({
        next: (adjustment) => {
          this.busy.set(false);
          this.quantity.set('');
          this.comment.set('');
          this.productResource.reload();
          this.adjustmentResource.reload();
          this.toasts.show(
            `Stock de ${adjustment.product.name} corrigé : ${this.signed(adjustment)} ` +
              `(${this.reasonLabel(adjustment.reason)}).`,
          );
        },
        error: () => this.busy.set(false),
      });
  }
}
