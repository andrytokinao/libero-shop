import { Component, ElementRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../../core/api/api-resource';
import { CatalogApi } from '../../../core/api/catalog.api';
import { CostingApi } from '../../../core/api/costing.api';
import { StockApi } from '../../../core/api/stock.api';
import { CodeMatch } from '../../../core/catalog/product-pick';
import { ProductCost, Supplier } from '../../../core/models';
import { formatAmount, unitChoiceLabel, unitsOf } from '../../../core/sale/sale-unit';
import { StockUndo } from '../../../core/stock/stock-undo';
import { ToastService } from '../../../core/services/toast.service';
import {
  DeliveryLine,
  addToNote,
  deliveryRequest,
  lastCostText,
  lineTotal,
  noteTotal,
  unitOf,
} from '../../../core/stock/delivery-note';
import { ProductStore } from '../../../core/store/product.store';
import { isTopmostDialog } from '../../../core/ui/topmost-dialog';
import { AriaryPipe } from '../../pipes/ariary.pipe';
import { ProductPickerComponent } from '../product-picker.component';
import { SupplierCreateDialogComponent } from '../supplier-create-dialog.component';
import { HelpButtonComponent } from '../help-button.component';

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
  templateUrl:'delivery-dialog.component.html',
  styleUrl:'delivery-dialog.component.scss'
})
export class DeliveryDialogComponent implements OnInit {
  private readonly stock = inject(StockApi);
  protected readonly unitChoiceLabel = unitChoiceLabel;
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
