import { Injectable, computed, inject } from '@angular/core';
import { Product, RoleApp } from '../models';
import { AuthService } from '../services/auth.service';

/** What can be done to one product from its menu. */
export type ProductAction = 'addStock' | 'edit' | 'adjust' | 'history';

/** The parts of a product's configuration, one tab each. */
export type ConfigTab = 'fiche' | 'prix' | 'unites' | 'codes';

/** A product's menu entry picked, or the "Nouveau produit" button. */
export type ProductDialogRequest =
  | { readonly action: 'create' }
  | {
      readonly action: ProductAction;
      readonly product: Product;
      /** Where the dialog puts the cursor: the purchase price, to fill in a missing one. */
      readonly focus?: 'cost';
      /** The configuration tab it opens on; the sheet ("Fiche") otherwise. */
      readonly tab?: ConfigTab;
    };

/**
 * Who may write the catalogue and the stock, said once for every screen that shows a product.
 *
 * <p>Mirrors the `@PreAuthorize` of ProductController, ProductUnitController and StockController:
 * the stock manager and the administrator own the product sheets and the corrections, and the
 * stock manager alone books goods in. Like `*appHasRole`, this keeps the screens honest — the
 * server is what refuses.
 */
@Injectable({ providedIn: 'root' })
export class ProductRights {
  private readonly auth = inject(AuthService);

  /** Creating a product, and correcting its sheet and its units. */
  readonly canEdit = computed(() =>
    this.auth.hasRole(RoleApp.DEPOT_MANAGER, RoleApp.SUPER_ADMIN),
  );
  /** Booking goods into the stock. */
  readonly canAddStock = computed(() => this.auth.hasRole(RoleApp.DEPOT_MANAGER));
  /** Breakage, loss, a shelf counted. */
  readonly canAdjustStock = this.canEdit;
  /** A product's movements carry purchase prices: the readers of the receipts. */
  readonly canSeeHistory = this.canEdit;

  /** The entries of a product's menu this account is offered, in the order they are shown. */
  readonly actions = computed<readonly { id: ProductAction; label: string }[]>(() => [
    ...(this.canAddStock() ? [{ id: 'addStock' as const, label: 'Ajouter du stock' }] : []),
    ...(this.canEdit() ? [{ id: 'edit' as const, label: 'Configurer (prix, unités)' }] : []),
    ...(this.canAdjustStock() ? [{ id: 'adjust' as const, label: 'Corriger le stock' }] : []),
    ...(this.canSeeHistory() ? [{ id: 'history' as const, label: 'Historique du stock' }] : []),
  ]);
}
