import { Component, effect, inject, input, output, signal } from '@angular/core';
import { ProductDialogRequest, ProductRights } from '../../core/catalog/product-rights';
import { CatalogApi } from '../../core/api/catalog.api';
import { Product } from '../../core/models';
import { ToastAction, ToastService } from '../../core/services/toast.service';
import { formatAmount } from '../../core/sale/sale-unit';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { ProductCreateDialogComponent } from './product-create-dialog.component';
import { ProductOverviewDialogComponent } from './product-overview-dialog.component';
import { ProductUnitsDialogComponent } from './product-units-dialog.component';
import { StockAdjustDialogComponent } from './stock-adjust-dialog.component';
import { StockEntryDialogComponent } from './stock-entry-dialog.component';
import { StockHistoryDialogComponent } from './stock-history-dialog.component';

/**
 * The dialog behind each entry of a product's menu, and behind "Nouveau produit" — one of them
 * at a time, for a whole screen.
 *
 * <p>A screen showing products holds one of these beside its list, hands it what a menu asked
 * for, and hears when the catalogue or the stock changed. Which dialog, and what it needs, is
 * decided here, so a screen that shows products gains every action by adding two lines.
 *
 * <p>One dialog may lead to another — the configuration's "Ajouter du stock" — without the
 * screen hearing of it: the one shown is what was asked, until a dialog asks for the next.
 */
@Component({
  selector: 'app-product-dialogs',
  standalone: true,
  imports: [
    ProductCreateDialogComponent,
    ProductOverviewDialogComponent,
    ProductUnitsDialogComponent,
    StockAdjustDialogComponent,
    StockEntryDialogComponent,
    StockHistoryDialogComponent,
    ConfirmDialogComponent,
  ],
  template: `
    @if (shown(); as asked) {
      @switch (asked.action) {
        @case ('create') {
          <app-product-create-dialog
            [copyOf]="asked.copyOf ?? null"
            [announce]="false"
            (created)="onCreated($event)"
            (closed)="closed.emit()"
          />
        }
        @case ('edit') {
          <app-product-units-dialog
            [productId]="asked.product.id"
            [productName]="asked.product.name"
            [focusCost]="asked.focus === 'cost'"
            [focusThreshold]="asked.focus === 'threshold'"
            [initialTab]="asked.tab"
            (changed)="changed.emit()"
            (addStock)="shown.set({ action: 'addStock', product: $event })"
            (closed)="closed.emit()"
          />
        }
        @case ('addStock') {
          <app-stock-entry-dialog
            [product]="asked.product"
            (changed)="changed.emit()"
            (closed)="closed.emit()"
          />
        }
        @case ('adjust') {
          <app-stock-adjust-dialog
            [product]="asked.product"
            [initialReason]="asked.count ? 'INVENTORY_COUNT' : 'BREAKAGE'"
            (changed)="changed.emit()"
            (closed)="closed.emit()"
          />
        }
        @case ('overview') {
          <app-product-overview-dialog
            [product]="asked.product"
            (picked)="shown.set($event)"
            (closed)="closed.emit()"
          />
        }
        @case ('history') {
          <app-stock-history-dialog [product]="asked.product" (closed)="closed.emit()" />
        }
        @case ('retire') {
          @if (asked.product.retired) {
            <app-confirm-dialog
              title="Remettre en vente ?"
              [message]="'« ' + asked.product.name + ' » sera de nouveau proposé aux caisses et aux commandes.'"
              confirmLabel="Remettre en vente"
              [busy]="busy()"
              (confirmed)="setRetired(asked.product, false)"
              (closed)="closed.emit()"
            />
          } @else {
            <app-confirm-dialog
              title="Retirer de la vente ?"
              [message]="'« ' + asked.product.name + ' » ne sera plus proposé aux caisses ni aux commandes. Son stock, son historique et ses factures restent ; il peut être remis en vente.'"
              confirmLabel="Retirer de la vente"
              [danger]="true"
              [busy]="busy()"
              (confirmed)="setRetired(asked.product, true)"
              (closed)="closed.emit()"
            />
          }
        }
      }
    }
  `,
})
export class ProductDialogsComponent {
  /** What was asked for, or null while nothing is open. */
  readonly request = input.required<ProductDialogRequest | null>();
  /** A product was created or edited, or its stock moved: the lists are out of date. */
  readonly changed = output<void>();
  readonly closed = output<void>();

  /** The dialog on screen: the one asked for, or the one a dialog led to. */
  protected readonly shown = signal<ProductDialogRequest | null>(null);

  constructor() {
    effect(() => this.shown.set(this.request()), { allowSignalWrites: true });
  }

  private readonly api = inject(CatalogApi);
  private readonly toasts = inject(ToastService);
  private readonly rights = inject(ProductRights);
  protected readonly busy = signal(false);

  /**
   * A new product: said, with what usually comes next — its stock, its codes — one tap away in
   * the message, for whoever may do it.
   */
  protected onCreated(product: Product): void {
    this.changed.emit();
    const next: ToastAction[] = [];
    if (this.rights.canAddStock()) {
      next.push({ label: 'Ajouter du stock', run: () => this.shown.set({ action: 'addStock', product }) });
    }
    next.push({ label: 'Codes-barres', run: () => this.shown.set({ action: 'edit', product, tab: 'codes' }) });
    const stock = product.stockQuantity > 0 ? `, avec ${formatAmount(product.stockQuantity, product.unit)} en stock` : '';
    this.toasts.show(`Produit « ${product.name} » créé${stock}.`, next);
  }

  /** "Retirer de la vente" or "Remettre en vente", once confirmed. */
  protected setRetired(product: Product, retired: boolean): void {
    this.busy.set(true);
    this.api.changeRetired(product.id, retired).subscribe({
      next: () => {
        this.busy.set(false);
        this.toasts.show(retired ? `« ${product.name} » retiré de la vente.` : `« ${product.name} » remis en vente.`);
        this.changed.emit();
        this.closed.emit();
      },
      error: () => this.busy.set(false),
    });
  }
}
