import { Component, ElementRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { CostingApi } from '../../core/api/costing.api';
import { StockApi } from '../../core/api/stock.api';
import { CodeMatch } from '../../core/catalog/product-pick';
import { ProductCost, Supplier } from '../../core/models';
import { formatAmount, unitsOf } from '../../core/sale/sale-unit';
import { StockUndo } from '../../core/stock/stock-undo';
import { ToastService } from '../../core/services/toast.service';
import {
  DeliveryLine,
  addToNote,
  deliveryRequest,
  lastCostText,
  lineTotal,
  noteTotal,
  unitOf,
} from '../../core/stock/delivery-note';
import { ProductStore } from '../../core/store/product.store';
import { isTopmostDialog } from '../../core/ui/topmost-dialog';
import { AriaryPipe } from '../pipes/ariary.pipe';
import { ProductPickerComponent } from './product-picker.component';
import { SupplierCreateDialogComponent } from './supplier-create-dialog.component';
import { HelpButtonComponent } from './help-button.component';

/**
 * A delivery note typed in one go: the supplier, then a line per product — scanned or picked —
 * with its unit, its quantity and its price, the last one paid already there. One "Enregistrer"
 * books them all, or none if one is refused.
 *
 * <p>The page "Approvisionnement" keeps the receipts' history and the price comparison; this is
 * for the delivery with ten products on it, which used to be ten forms.
 */
@Component({
  selector: 'app-delivery-dialog',
  standalone: true,
  imports: [HelpButtonComponent, FormsModule, AriaryPipe, ProductPickerComponent, SupplierCreateDialogComponent],
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal wide dn" role="dialog" aria-modal="true" aria-labelledby="dn-title">
        <div class="modal-head">
          <div>
            <h2 id="dn-title">Réception — bon de livraison</h2>
            <div class="sub muted">Un fournisseur, une ligne par produit, un seul enregistrement.</div>
          </div>
          <app-help topic="delivery" />
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          <div class="dn-top">
            <div class="fld">
              <label for="dn-supplier">Fournisseur</label>
              <div class="dn-supplier">
                <select
                  id="dn-supplier"
                  class="field"
                  [ngModel]="supplierId()"
                  (ngModelChange)="supplierId.set($event)"
                >
                  <option [ngValue]="null">Sans fournisseur</option>
                  @for (supplier of suppliers(); track supplier.id) {
                    <option [ngValue]="supplier.id">{{ supplier.name }}</option>
                  }
                </select>
                <button class="btn small ghost" type="button" title="Nouveau fournisseur" (click)="creatingSupplier.set(true)">
                  ＋
                </button>
              </div>
            </div>
            <div class="fld dn-add">
              <label for="dn-product">Ajouter un produit</label>
              <app-product-picker
                inputId="dn-product"
                [products]="products()"
                (picked)="add($event)"
                (unknownCode)="toasts.show('Aucun produit ne porte le code ' + $event + '.')"
              />
            </div>
          </div>

          @if (lines().length) {
            <div class="dn-lines">
              @for (line of lines(); track $index; let i = $index) {
                <div class="dn-line">
                  <div class="dn-name">{{ line.product.name }}</div>
                  <div class="fld dn-unit">
                    <label [for]="'dn-unit-' + i">Reçu en</label>
                    <select
                      class="field"
                      [id]="'dn-unit-' + i"
                      [ngModel]="line.packagingId"
                      (ngModelChange)="setUnit(i, $event)"
                    >
                      @for (unit of unitsOf(line.product); track unit.packagingId) {
                        <option [ngValue]="unit.packagingId">{{ unit.label ?? 'unité' }}</option>
                      }
                    </select>
                  </div>
                  <div class="fld dn-qty">
                    <label [for]="'dn-qty-' + i">Quantité</label>
                    <input
                      type="text"
                      inputmode="decimal"
                      autocomplete="off"
                      [id]="'dn-qty-' + i"
                      [ngModel]="line.quantity"
                      (ngModelChange)="patch(i, { quantity: $event })"
                    />
                  </div>
                  <div class="fld dn-cost">
                    <label [for]="'dn-cost-' + i">Prix par {{ unitOf(line).label ?? 'unité' }}</label>
                    <input
                      type="text"
                      inputmode="decimal"
                      autocomplete="off"
                      placeholder="inconnu"
                      [id]="'dn-cost-' + i"
                      [ngModel]="line.unitCost"
                      (ngModelChange)="patch(i, { unitCost: $event })"
                    />
                  </div>
                  <div class="dn-total">
                    @if (lineTotal(line); as total) {
                      {{ total | ariary }}
                    } @else {
                      <span class="muted">—</span>
                    }
                  </div>
                  <button class="x dn-remove" type="button" [attr.aria-label]="'Retirer ' + line.product.name" (click)="remove(i)">
                    ✕
                  </button>
                </div>
              }
            </div>
          } @else {
            <div class="empty">Scannez ou cherchez le premier produit du bon.</div>
          }
        </div>

        <div class="modal-foot">
          <div class="dn-sum">
            Total : <strong>{{ total().amount | ariary }}</strong>
            @if (total().unpriced) {
              <span class="muted">&nbsp;· {{ total().unpriced }} ligne(s) sans prix</span>
            }
          </div>
          <button class="btn ghost" type="button" (click)="closed.emit()">Annuler</button>
          <button class="btn" type="button" [disabled]="busy() || !request()" (click)="save()">
            {{ busy() ? 'Enregistrement...' : 'Enregistrer la réception (' + lines().length + ')' }}
          </button>
        </div>
      </div>
    </div>

    @if (creatingSupplier()) {
      <app-supplier-create-dialog
        hint="Il sera choisi pour ce bon de livraison."
        (created)="onSupplierCreated($event)"
        (closed)="creatingSupplier.set(false)"
      />
    }
  `,
  styles: `
    .dn-top {
      display: grid;
      grid-template-columns: minmax(200px, 1fr) minmax(240px, 2fr);
      gap: 12px;
      margin-bottom: 14px;
    }

    .dn-supplier {
      display: flex;
      gap: 6px;

      select {
        flex: 1;
        min-width: 0;
      }
    }

    .dn-line {
      display: grid;
      grid-template-columns: minmax(140px, 2fr) minmax(90px, 1fr) 90px 120px 110px 30px;
      align-items: end;
      gap: 8px;
      padding: 10px 0;
      border-top: 1px solid var(--line);

      .fld {
        margin-bottom: 0;
        min-width: 0;
      }

      input,
      select {
        width: 100%;
        min-width: 0;
      }
    }

    .dn-name {
      font-weight: 600;
      align-self: center;
    }

    .dn-total {
      text-align: right;
      align-self: center;
      font-variant-numeric: tabular-nums;
    }

    .dn-remove {
      align-self: center;
    }

    .dn-sum {
      margin-right: auto;
    }

    /* A line becomes a small card: no sideways scroll on a phone. */
    @media (max-width: 700px) {
      .dn-top {
        grid-template-columns: 1fr;
      }

      .dn-line {
        grid-template-columns: 1fr 1fr 30px;
        grid-template-areas:
          'name name remove'
          'unit qty qty'
          'cost total total';

        .dn-name {
          grid-area: name;
        }

        .dn-remove {
          grid-area: remove;
        }

        .dn-unit {
          grid-area: unit;
        }

        .dn-qty {
          grid-area: qty;
        }

        .dn-cost {
          grid-area: cost;
        }

        .dn-total {
          grid-area: total;
          align-self: end;
          padding-bottom: 8px;
        }
      }
    }
  `,
})
export class DeliveryDialogComponent implements OnInit {
  private readonly stock = inject(StockApi);
  private readonly catalog = inject(CatalogApi);
  private readonly costing = inject(CostingApi);
  protected readonly toasts = inject(ToastService);
  private readonly undo = inject(StockUndo);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** The note was booked: the stock lists are out of date. */
  /** "Nouvelle réception" from a supplier's menu: the note starts with it. */
  readonly initialSupplierId = input<number | null>(null);
  readonly changed = output<void>();
  readonly closed = output<void>();

  protected readonly products = inject(ProductStore).list().value;
  private readonly supplierResource = apiResource<Supplier[]>([], () => this.catalog.suppliers());
  /** The suppliers still offered — and the one picked, even if it was set aside meanwhile. */
  protected readonly suppliers = computed(() =>
    this.supplierResource.value().filter((s) => !s.retired || s.id === this.supplierId()),
  );
  private readonly costs = apiResource<ProductCost[]>([], () => this.costing.productCosts()).value;

  protected readonly supplierId = signal<number | null>(null);
  protected readonly lines = signal<DeliveryLine[]>([]);
  protected readonly busy = signal(false);
  protected readonly creatingSupplier = signal(false);

  protected readonly total = computed(() => noteTotal(this.lines()));

  /** Here rather than in a field: the inputs are not there yet before this. */
  ngOnInit(): void {
    this.supplierId.set(this.initialSupplierId());
  }
  protected readonly request = computed(() => deliveryRequest(this.supplierId(), this.lines()));

  protected readonly unitsOf = unitsOf;
  protected readonly unitOf = unitOf;
  protected readonly lineTotal = lineTotal;

  protected add(match: CodeMatch): void {
    this.lines.update((lines) => addToNote(lines, match.product, match.packagingId, this.costOf(match.product.id)));
  }

  protected setUnit(index: number, packagingId: number | null): void {
    const line = this.lines()[index];
    // A carton's price is not a piece's: the field starts again from the last one paid.
    this.patch(index, { packagingId, unitCost: lastCostText(this.costOf(line.product.id), packagingId) });
  }

  protected patch(index: number, change: Partial<DeliveryLine>): void {
    this.lines.update((lines) => lines.map((line, i) => (i === index ? { ...line, ...change } : line)));
  }

  protected remove(index: number): void {
    this.lines.update((lines) => lines.filter((_, i) => i !== index));
  }

  protected onSupplierCreated(supplier: Supplier): void {
    this.supplierResource.reload();
    this.supplierId.set(supplier.id);
  }

  protected save(): void {
    const request = this.request();
    if (!request || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.stock.registerDelivery(request).subscribe({
      next: (supplies) => {
        const amount = this.total().amount;
        this.undo.received(
          `Réception enregistrée : ${supplies.length} produit(s)` +
            (amount ? `, ${formatAmount(amount, null)} Ar.` : '.'),
          supplies,
        );
        this.changed.emit();
        this.closed.emit();
      },
      // Nothing was booked: the error toast says which line, the note stays as typed.
      error: () => this.busy.set(false),
    });
  }

  private costOf(productId: number): ProductCost | undefined {
    return this.costs().find((cost) => cost.productId === productId);
  }

  protected onEscape(): void {
    if (isTopmostDialog(this.host.nativeElement)) {
      this.closed.emit();
    }
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
