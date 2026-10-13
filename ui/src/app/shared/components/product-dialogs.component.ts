import { Component, input, output } from '@angular/core';
import { ProductDialogRequest } from '../../core/catalog/product-rights';
import { ProductCreateDialogComponent } from './product-create-dialog.component';
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
 */
@Component({
  selector: 'app-product-dialogs',
  standalone: true,
  imports: [
    ProductCreateDialogComponent,
    ProductUnitsDialogComponent,
    StockAdjustDialogComponent,
    StockEntryDialogComponent,
    StockHistoryDialogComponent,
  ],
  template: `
    @if (request(); as asked) {
      @switch (asked.action) {
        @case ('create') {
          <app-product-create-dialog (created)="changed.emit()" (closed)="closed.emit()" />
        }
        @case ('edit') {
          <app-product-units-dialog
            [productId]="asked.product.id"
            [productName]="asked.product.name"
            (changed)="changed.emit()"
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
            (changed)="changed.emit()"
            (closed)="closed.emit()"
          />
        }
        @case ('history') {
          <app-stock-history-dialog [product]="asked.product" (closed)="closed.emit()" />
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
}
