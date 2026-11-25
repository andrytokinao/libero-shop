import {Component, computed, inject, input, output} from '@angular/core';
import { ProductMenuId, productMenu, requestFor } from '../../core/catalog/product-menu';
import {ProductDialogRequest, ProductRights} from '../../core/catalog/product-rights';
import { Product } from '../../core/models';
import { ActionMenuComponent } from './action-menu.component';
import {ProductDialogs} from '../services/product-dialogs.service';

/**
 * The "⋮" of one product: its stock, its configuration tab by tab, and the rest — whichever of
 * them the account signed in may do, and nothing at all for an account that may do none.
 *
 * <p>The matching dialog is opened by the `ProductDialogs` service, which is shared: a table of
 * two hundred rows carries two hundred buttons and not two hundred dialogs.
 */
@Component({
  selector: 'app-product-menu',
  standalone: true,
  imports: [ActionMenuComponent],
  template: `
    @if (entries().length) {
      <app-action-menu
        [actions]="entries()"
        [label]="'Actions sur ' + product().name"
        [openNow]="openNow()"
        (picked)="pick($event)"
      />
    }
  `,
})
export class ProductMenuComponent {
  private readonly rights = inject(ProductRights);
  private readonly dialogs = inject(ProductDialogs);

  readonly product = input.required<Product>();
  /** Opened at once — the product a scanned code designates. */
  readonly openNow = input(false);

  protected readonly entries = computed(() => productMenu(this.product(), this.rights.menuRights()));

  protected pick(id: ProductMenuId): void {
    void this.dialogs.open(requestFor(id, this.product())).then();
  }
}
