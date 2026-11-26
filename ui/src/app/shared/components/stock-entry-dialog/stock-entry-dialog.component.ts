import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../../core/api/api-resource';
import { CatalogApi } from '../../../core/api/catalog.api';
import { CostingApi } from '../../../core/api/costing.api';
import { StockApi } from '../../../core/api/stock.api';
import { Product, ProductCost, Supplier } from '../../../core/models';
import {
  SaleUnit,
  formatAmount,
  parseQuantity,
  roundQuantity,
  unitChoiceLabel,
  unitsOf,
} from '../../../core/sale/sale-unit';
import { StockUndo } from '../../../core/stock/stock-undo';
import { HelpButtonComponent } from '../help-button.component';
import {DIALOG_DATA, DialogRef} from '@angular/cdk/dialog';
import {ProductDialogRequest} from '../../../core/catalog/product-rights';

/**
 * Adds stock to one product, from its menu: how many, in which unit, at what price, from whom.
 *
 * <p>The same receipt as the "Approvisionnement" screen books, reached from the product instead
 * of the other way round — so it is kept in the same history, with who entered it and when. Only
 * the quantity is required: the supplier and the purchase price are said when they are known.
 */
@Component({
  selector: 'app-stock-entry-dialog',
  standalone: true,
  imports: [HelpButtonComponent, FormsModule],
  host: { '(document:keydown.escape)': 'close()' },
  templateUrl:'stock-entry-dialog.component.html',
  styleUrl:'stock-entry-dialog.component.scss',
})
export class StockEntryDialogComponent implements OnInit {
  dialogRef = inject(DialogRef<{ saved: boolean }>);
  close(){
    this.dialogRef.close({saved:false});
  }
  data = inject<ProductDialogRequest>(DIALOG_DATA);
  readonly product = signal(this.data.product);
  private readonly stock = inject(StockApi);
  protected readonly unitChoiceLabel = unitChoiceLabel;
  private readonly catalog = inject(CatalogApi);
  private readonly costing = inject(CostingApi);
  private readonly undo = inject(StockUndo);

  /** After the entry is booked: the lists showing this product's stock are out of date. */
  readonly changed = output<void>();

  /** As typed: strings, because an empty field is not zero. */
  protected readonly quantity = signal('');
  /** Undefined until the operator touches the field: the last price paid is offered meanwhile. */
  protected readonly typedCost = signal<string | undefined>(undefined);
  protected readonly unitId = signal<number | null>(null);
  protected readonly supplierId = signal<number | null>(null);
  protected readonly busy = signal(false);

  private readonly supplierResource = apiResource<Supplier[]>([], () => this.catalog.suppliers());
  /** Every unit with the last price paid for it, to pre-fill the next one; null until read. */
  private readonly costs = signal<ProductCost | null>(null);

  protected readonly suppliers = this.supplierResource.value;
  protected readonly units = computed(() => unitsOf(this.product()));
  protected readonly selectedUnit = computed<SaleUnit>(
    () => this.units().find((unit) => unit.packagingId === this.unitId()) ?? this.units()[0],
  );

  /** What the cost field shows: what was typed, else the last price paid, scaled to the unit. */
  protected readonly cost = computed(() => {
    const typed = this.typedCost();
    if (typed !== undefined) {
      return typed;
    }
    const unit = this.selectedUnit();
    const last = this.costs()?.units.find((u) => u.packagingId === unit.packagingId)?.lastCost;
    return last == null ? '' : String(Math.round(last));
  });

  protected readonly quantityNumber = computed(() => parseQuantity(this.quantity()));
  /** Null for an empty field — "not given", never zero — and undefined for one that cannot be read. */
  private readonly costNumber = computed<number | null | undefined>(() => {
    const text = this.cost().replace(/\s/g, '').replace(',', '.');
    if (!text) {
      return null;
    }
    return /^\d+(\.\d{1,2})?$/.test(text) ? Number(text) : undefined;
  });

  protected readonly preview = computed(() => {
    const quantity = this.quantityNumber();
    if (quantity === null) {
      return null;
    }
    const product = this.product();
    const received = roundQuantity(quantity * this.selectedUnit().factor);
    const after = roundQuantity(product.stockQuantity + received);
    return `Stock : ${formatAmount(product.stockQuantity, product.unit)} → ${formatAmount(after, product.unit)} (+${formatAmount(received, product.unit)}).`;
  });

  protected readonly formatAmount = formatAmount;

  /**
   * Here rather than in a field: the product is an input, and is not there yet before this. Opens
   * on the unit the product is bought in — rice bought by the tonne is received in tonnes.
   */
  ngOnInit(): void {
    this.costing.productCost(this.product().id).subscribe((costs) => {
      this.costs.set(costs);
      const bought = costs.purchaseUnitId;
      if (this.unitId() === null && this.units().some((unit) => unit.packagingId === bought)) {
        this.unitId.set(bought);
      }
    });
  }

  //entré de protui ( uto tompletiondes protuis existant ) 30
  // Entré des charge 30
  // definition de prix de vente 30
  // Auto calcule de benefice 30

  protected pickUnit(packagingId: number | null): void {
    this.unitId.set(packagingId);
    // A price typed for a kapoka is not a sack's: start again from the last price, scaled.
    this.typedCost.set(undefined);
  }

  protected canSubmit(): boolean {
    return !this.busy() && this.quantityNumber() !== null && this.costNumber() !== undefined;
  }

  protected register(): void {
    const quantity = this.quantityNumber();
    const unitCost = this.costNumber();
    if (!this.canSubmit() || quantity === null || unitCost === undefined) {
      return;
    }
    this.busy.set(true);
    this.stock
      .registerSupply({
        productId: this.product().id,
        supplierId: this.supplierId(),
        packagingId: this.selectedUnit().packagingId,
        quantity,
        unitCost,
      })
      .subscribe({
        next: (supply) => {
          this.undo.received(
            `${formatAmount(supply.receivedQuantity, supply.unitLabel ?? supply.product.unit)} de ${supply.product.name} ajouté(s) au stock.`,
            [supply],
          );
          this.changed.emit();
          this.close();
        },
        error: () => this.busy.set(false),
      });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.close();
    }
  }
}
