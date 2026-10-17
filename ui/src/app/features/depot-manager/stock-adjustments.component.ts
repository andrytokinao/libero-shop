import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { StockApi } from '../../core/api/stock.api';
import { ADJUSTMENT_REASONS, AdjustmentReason, Product, StockAdjustment, adjustmentReasonLabel } from '../../core/models';
import { MovementAction, adjustmentActions, countsAsLoss } from '../../core/stock/movement-actions';
import {
  formatAmount,
  formatQuantity,
  parseQuantity,
  roundQuantity,
  unitChoiceLabel,
  unitsOf,
} from '../../core/sale/sale-unit';
import { conversionLine, inBaseUnits } from '../../core/stock/adjustment-unit';
import { StockUndo } from '../../core/stock/stock-undo';
import { ToastService } from '../../core/services/toast.service';
import { ProductStore } from '../../core/store/product.store';
import { ActionMenuComponent } from '../../shared/components/action-menu.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { StockHistoryDialogComponent } from '../../shared/components/stock-history-dialog.component';
import { ProductPickerComponent } from '../../shared/components/product-picker.component';
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
  imports: [
    FormsModule,
    DatePipe,
    KpiCardComponent,
    AriaryPipe,
    ProductPickerComponent,
    ActionMenuComponent,
    ConfirmDialogComponent,
    StockHistoryDialogComponent,
  ],
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
          <app-product-picker
            inputId="adjust-product"
            [products]="products()"
            [selected]="selectedProduct()"
            (picked)="productId.set($event.product.id)"
            (unknownCode)="sayUnknown($event)"
          />
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
            {{ isCount() ? 'Quantité comptée' : 'Quantité perdue' }} ({{ unit()?.label ?? 'unité' }})
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
          @if (conversion(); as line) {
            <div class="muted" style="font-size:12px;">{{ line }}</div>
          }
        </div>
        <!-- "2 cartons cassés" rather than "48 pièces": counted in the unit at hand. -->
        @if (units().length > 1) {
          <div class="fld">
            <label for="adjust-unit">En</label>
            <select
              id="adjust-unit"
              name="unit"
              class="field"
              [ngModel]="unit()?.packagingId ?? null"
              (ngModelChange)="unitId.set($event)"
            >
              @for (choice of units(); track choice.packagingId) {
                <option [ngValue]="choice.packagingId">{{ unitChoiceLabel(choice) }}</option>
              }
            </select>
          </div>
        }
        <div class="fld" style="flex:1; min-width:200px;">
          <label for="adjust-comment">
            Commentaire {{ reason() === 'OTHER' ? '(obligatoire)' : '(facultatif)' }}
          </label>
          <input
            id="adjust-comment"
            name="comment"
            type="text"
            autocomplete="off"
            maxlength="200"
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
        <div class="table-scroll">
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
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (adjustment of adjustments(); track adjustment.id) {
                <tr [class.adj-cancelled]="adjustment.cancelledAt">
                  <td class="muted">{{ adjustment.movementDate | date: 'dd/MM/yy HH:mm' }}</td>
                  <td>
                    {{ adjustment.product.name }}
                    @if (adjustment.cancelledAt) {
                      <span class="badge grey">Annulée</span>
                    }
                  </td>
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
                  <td class="num">
                    <app-action-menu
                      [actions]="adjustmentActions(adjustment)"
                      [label]="'Actions sur la correction de ' + adjustment.product.name"
                      (picked)="onAdjustmentAction(adjustment, $event)"
                    />
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <div class="empty">Aucune correction de stock pour l'instant.</div>
      }
    </div>

    @if (historyOf(); as product) {
      <app-stock-history-dialog [product]="product" (closed)="historyOf.set(null)" />
    }

    @if (cancelling(); as adjustment) {
      <app-confirm-dialog
        title="Annuler cette correction ?"
        [message]="'Le stock de « ' + adjustment.product.name + ' » revient de ' + signed(adjustment) + '. La correction reste dans l’historique, marquée annulée.'"
        confirmLabel="Annuler la correction"
        [danger]="true"
        [busy]="busy()"
        (confirmed)="cancelAdjustment(adjustment)"
        (closed)="cancelling.set(null)"
      />
    }
  `,
  styles: `
    .adj-cancelled td {
      color: var(--ink-soft);
    }

    .warn {
      color: var(--danger, #c0392b);
    }
  `,
})
export class StockAdjustmentsComponent {
  private readonly api = inject(StockApi);
  protected readonly unitChoiceLabel = unitChoiceLabel;
  private readonly toasts = inject(ToastService);
  private readonly undo = inject(StockUndo);

  constructor() {
    // Taken back from the message: the lists show the movement cancelled.
    this.undo.undone$.pipe(takeUntilDestroyed()).subscribe(() => {
      this.adjustmentResource.reload();
      this.productResource.reload();
    });
  }

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
  protected readonly units = computed(() => {
    const product = this.selectedProduct();
    return product ? unitsOf(product) : [];
  });
  /** The packaging the quantity is said in; null for the base unit. Another product: its base unit. */
  protected readonly unitId = signal<number | null>(null);
  protected readonly unit = computed(
    () => this.units().find((unit) => unit.packagingId === this.unitId()) ?? this.units()[0],
  );
  protected readonly isCount = computed(() => this.reason() === 'INVENTORY_COUNT');

  protected readonly quantityNumber = computed(() => parseQuantity(this.quantity()));
  protected readonly conversion = computed(() => {
    const product = this.selectedProduct();
    const unit = this.unit();
    return product && unit ? conversionLine(this.quantityNumber(), unit, product) : null;
  });

  /** What the stock would move by, from the figure this screen holds; the server decides. */
  private readonly difference = computed<number | null>(() => {
    const product = this.selectedProduct();
    const quantity = this.quantityNumber();
    const unit = this.unit();
    if (!product || quantity === null || !unit) {
      return null;
    }
    const base = inBaseUnits(quantity, unit);
    return roundQuantity(this.isCount() ? base - product.stockQuantity : -base);
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
      this.adjustments().reduce((total, a) => total + (countsAsLoss(a) ? -a.quantity : 0), 0),
    ),
  );
  protected readonly lostValue = computed(() =>
    this.adjustments().reduce(
      (total, a) => total + (countsAsLoss(a) && a.costValue !== null && a.costValue < 0 ? -a.costValue : 0),
      0,
    ),
  );

  protected readonly formatQuantity = formatQuantity;

  protected reasonLabel(reason: AdjustmentReason): string {
    return adjustmentReasonLabel(reason);
  }

  protected readonly adjustmentActions = adjustmentActions;
  protected readonly historyOf = signal<Product | null>(null);
  protected readonly cancelling = signal<StockAdjustment | null>(null);

  protected onAdjustmentAction(adjustment: StockAdjustment, action: MovementAction): void {
    if (action === 'history') {
      this.historyOf.set(adjustment.product);
    } else if (action === 'cancel') {
      this.cancelling.set(adjustment);
    }
  }

  /** Undone by an opposite correction: the stock goes back, both stay in the history. */
  protected cancelAdjustment(adjustment: StockAdjustment): void {
    this.busy.set(true);
    this.api.cancelAdjustment(adjustment.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.cancelling.set(null);
        this.adjustmentResource.reload();
        this.productResource.reload();
        this.toasts.show(`Correction de « ${adjustment.product.name} » annulée ; le stock est corrigé.`);
      },
      error: () => {
        this.busy.set(false);
        this.cancelling.set(null);
      },
    });
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

  protected sayUnknown(code: string): void {
    this.toasts.show(`Aucun produit ne porte le code ${code}.`);
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
        packagingId: this.unit()?.packagingId ?? null,
      })
      .subscribe({
        next: (adjustment) => {
          this.busy.set(false);
          this.quantity.set('');
          this.comment.set('');
          this.productResource.reload();
          this.adjustmentResource.reload();
          this.undo.corrected(
            `Stock de ${adjustment.product.name} corrigé : ${this.signed(adjustment)} ` +
              `(${this.reasonLabel(adjustment.reason)}).`,
            adjustment,
          );
        },
        error: () => this.busy.set(false),
      });
  }
}
