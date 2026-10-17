import { Product } from '../models';
import { formatAmount } from '../sale/sale-unit';
import { ProductDialogRequest } from './product-rights';

/** One entry of a product's "⋮". */
export type ProductMenuId =
  | 'addStock'
  | 'adjust'
  | 'count'
  | 'history'
  | 'sheet'
  | 'prices'
  | 'units'
  | 'barcodes'
  | 'threshold'
  | 'duplicate'
  | 'retire';

export interface ProductMenuEntry {
  readonly id: ProductMenuId;
  readonly label: string;
  /** The small heading it is listed under: three families are read, not thirteen lines. */
  readonly group: 'Stock' | 'Configuration' | 'Autres';
  /** On the right, in grey: what it does, or the value it changes ("10 sacs"). */
  readonly hint?: string;
  /** In red: an entry that takes something away. */
  readonly danger?: boolean;
}

/** What the account signed in may do; mirrors ProductRights. */
export interface ProductMenuRights {
  readonly canAddStock: boolean;
  readonly canAdjustStock: boolean;
  readonly canSeeHistory: boolean;
  readonly canEdit: boolean;
}

/**
 * The entries of a product's menu, by family, those the account may not use left out — an empty
 * list for whoever may do nothing, and then no "⋮" at all.
 */
export function productMenu(product: Product, rights: ProductMenuRights): ProductMenuEntry[] {
  const entries: (ProductMenuEntry | false)[] = [
    rights.canAddStock && { id: 'addStock', label: 'Ajouter du stock', group: 'Stock', hint: 'réception' },
    rights.canAdjustStock && { id: 'adjust', label: 'Corriger le stock', group: 'Stock', hint: 'casse, perte…' },
    rights.canAdjustStock && { id: 'count', label: 'Compter ce produit', group: 'Stock', hint: 'inventaire' },
    rights.canSeeHistory && { id: 'history', label: 'Historique du stock', group: 'Stock' },
    rights.canEdit && { id: 'sheet', label: 'Nom et rayon', group: 'Configuration' },
    rights.canEdit && { id: 'prices', label: "Prix de vente et d'entrée", group: 'Configuration' },
    rights.canEdit && { id: 'units', label: 'Unités et packaging', group: 'Configuration' },
    rights.canEdit && { id: 'barcodes', label: 'Codes-barres', group: 'Configuration' },
    rights.canEdit && {
      id: 'threshold',
      label: "Seuil d'alerte",
      group: 'Configuration',
      hint: product.lowStockThreshold == null ? undefined : formatAmount(product.lowStockThreshold, product.unit),
    },
    rights.canEdit && { id: 'duplicate', label: 'Dupliquer le produit', group: 'Autres' },
    rights.canEdit &&
      (product.retired
        ? { id: 'retire', label: 'Remettre en vente', group: 'Autres' }
        : { id: 'retire', label: 'Retirer de la vente', group: 'Autres', danger: true }),
  ];
  return entries.filter((entry): entry is ProductMenuEntry => entry !== false);
}

/** The dialog an entry opens: the configuration on the right tab, the stock dialogs, the copy. */
export function requestFor(id: ProductMenuId, product: Product): ProductDialogRequest {
  switch (id) {
    case 'addStock':
    case 'adjust':
    case 'history':
      return { action: id, product };
    case 'count':
      return { action: 'adjust', product, count: true };
    case 'sheet':
      return { action: 'edit', product, tab: 'fiche' };
    case 'prices':
      return { action: 'edit', product, tab: 'prix' };
    case 'units':
      return { action: 'edit', product, tab: 'unites' };
    case 'barcodes':
      return { action: 'edit', product, tab: 'codes' };
    case 'threshold':
      return { action: 'edit', product, tab: 'fiche', focus: 'threshold' };
    case 'duplicate':
      return { action: 'create', copyOf: product };
    case 'retire':
      return { action: 'retire', product };
  }
}
