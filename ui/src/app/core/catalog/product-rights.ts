import { Injectable, computed, inject } from '@angular/core';
import { Product, RoleApp } from '../models';
import { AuthService } from '../services/auth.service';

/** What can be done to one product from its menu. */
export type ProductAction = 'addStock' | 'edit' | 'adjust' | 'history' | 'retire';

/** The parts of a product's configuration, one tab each. */
export type ConfigTab = 'fiche' | 'prix' | 'unites' | 'codes';

/** A product's menu entry picked, or the "Nouveau produit" button. */
export type ProductDialogRequest =
  | {
      readonly action: 'create';
      /** "Dupliquer le produit": the new one starts from this one's sheet. */
      readonly copyOf?: Product;
    }
  | {
      readonly action: ProductAction;
      readonly product: Product;
      /** Where the dialog puts the cursor: the purchase price, to fill in a missing one. */
      readonly focus?: 'cost' | 'threshold';
      /** The configuration tab it opens on; the sheet ("Fiche") otherwise. */
      readonly tab?: ConfigTab;
      /** A correction that is a count of the shelf: "Compter ce produit". */
      readonly count?: boolean;
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

  /** What this account may do, as the product menu reads it. */
  readonly menuRights = computed(() => ({
    canAddStock: this.canAddStock(),
    canAdjustStock: this.canAdjustStock(),
    canSeeHistory: this.canSeeHistory(),
    canEdit: this.canEdit(),
  }));
}
