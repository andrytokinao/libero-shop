import { Component, inject, input, output } from '@angular/core';
import { ProductAction, ProductDialogRequest, ProductRights } from '../../core/catalog/product-rights';
import { Product } from '../../core/models';
import { ActionMenuComponent } from './action-menu.component';

/**
 * The "⋮" of one product: add stock, edit, correct, history — whichever of them the account
 * signed in may do, and nothing at all for an account that may do none.
 *
 * <p>It only says what was asked for. Opening the matching dialog is `app-product-dialogs`'s,
 * so a table of two hundred rows carries two hundred buttons and not two hundred dialogs.
 */
@Component({
  selector: 'app-product-menu',
  standalone: true,
  imports: [ActionMenuComponent],
  template: `
    @if (rights.actions().length) {
      <app-action-menu
        [actions]="rights.actions()"
        [label]="'Actions sur ' + product().name"
        (picked)="pick($event)"
      />
    }
  `,
})
export class ProductMenuComponent {
  protected readonly rights = inject(ProductRights);

  readonly product = input.required<Product>();
  readonly action = output<ProductDialogRequest>();

  protected pick(action: ProductAction): void {
    this.action.emit({ action, product: this.product() });
  }
}
