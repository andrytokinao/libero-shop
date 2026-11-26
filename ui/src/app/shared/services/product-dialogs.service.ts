import { Injectable, inject } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { ComponentType } from '@angular/cdk/portal';
import { Subject, firstValueFrom } from 'rxjs';
import { ProductDialogRequest, ProductRights } from '../../core/catalog/product-rights';
import { CatalogApi } from '../../core/api/catalog.api';
import { Product } from '../../core/models';
import { ToastAction, ToastService } from '../../core/services/toast.service';
import { formatAmount } from '../../core/sale/sale-unit';
import {ConfirmDialogComponent} from '../components/confirm-dialog.component';
import {ProductCreateDialogComponent} from '../components/product-create-dialog.component';

type Action = ProductDialogRequest['action'];

/** Ce qu'une modale renvoie en se fermant. */
export interface ProductDialogResult {
  /** Le catalogue ou le stock a changé : les listes sont périmées. */
  changed?: boolean;
  /** Une modale vient de créer ce produit. */
  created?: Product;
  /** Une modale demande qu'une autre s'ouvre à sa suite. */
  next?: ProductDialogRequest;
}

/** Type des données reçues par la modale d'une action : inject<ProductDialogData<'edit'>>(DIALOG_DATA). */
export type ProductDialogData<A extends Action> = Extract<ProductDialogRequest, { action: A }>;

type Loader = () => Promise<ComponentType<unknown>>;

/** Une nouvelle modale = une nouvelle ligne ici (et son cas dans ProductDialogRequest). */
const LOADERS: Record<Exclude<Action, 'retire'>, Loader> = {
  create: () => import('../../shared/components/product-create-dialog.component').then((m) => m.ProductCreateDialogComponent),
  edit: () => import('../../shared/components/product-units-dialog.component').then((m) => m.ProductUnitsDialogComponent),
  addStock: () => import('../components/stock-entry-dialog/stock-entry-dialog.component').then((m) => m.StockEntryDialogComponent),
  adjust: () => import('../../shared/components/stock-adjust-dialog.component').then((m) => m.StockAdjustDialogComponent),
  overview: () => import('../../shared/components/product-overview-dialog.component').then((m) => m.ProductOverviewDialogComponent),
  history: () => import('../../shared/components/stock-history-dialog.component').then((m) => m.StockHistoryDialogComponent),
  stockEntry: () => import('../../shared/components/stock-entry-charge-dialog/stock-entry-dialog-charge.component').then((m) => m.StockEntryDialogChargeComponent),
  // À compléter : ce composant doit suivre le même contrat (DIALOG_DATA / DialogRef), ou être ouvert par une route.
};

const WIDTHS: Partial<Record<Action, string>> = {
  edit: '900px',
  overview: '900px',
  history: '800px',
};

@Injectable({ providedIn: 'root' })
export class ProductDialogs {
  private readonly dialog = inject(Dialog);
  private readonly api = inject(CatalogApi);
  private readonly toasts = inject(ToastService);
  private readonly rights = inject(ProductRights);

  /** Émis quand le catalogue ou le stock a changé : les écrans rechargent leurs listes. */
  readonly changed = new Subject<void>();

  /** Ouvre la modale demandée, puis celles qu'elle enchaîne, jusqu'à la fermeture. */
  async open(request: ProductDialogRequest): Promise<void> {
    alert(JSON.stringify(request));
    let current: ProductDialogRequest | undefined = request;
    let changed = false;

    while (current) {
      const result: ProductDialogResult | undefined =
        current.action === 'retire' ? await this.retire(current.product) : await this.openOne(current);

      changed ||= !!result?.changed;
      if (result?.created) this.announceCreated(result.created);
      current = result?.next;
    }

    if (changed) this.changed.next();
  }

  private async openOne(request: ProductDialogRequest): Promise<ProductDialogResult | undefined> {
    const component = await LOADERS[request.action as Exclude<Action, 'retire'>]();
    const ref = this.dialog.open<ProductDialogResult>(component, {
      data: request,
      width: WIDTHS[request.action] ?? '660px',
      maxWidth: '95vw',
      panelClass: 'app-modal-panel',
      backdropClass: 'app-modal-backdrop',
    });
    return firstValueFrom(ref.closed);
  }

  /** "Retirer de la vente" / "Remettre en vente", après confirmation. */
  private async retire(product: Product): Promise<ProductDialogResult | undefined> {
    const retiring = !product.retired;
    const ref = this.dialog.open<boolean>(ConfirmDialogComponent, {
      data: retiring
        ? {
          title: 'Retirer de la vente ?',
          message: `« ${product.name} » ne sera plus proposé aux caisses ni aux commandes. Son stock, son historique et ses factures restent ; il peut être remis en vente.`,
          confirmLabel: 'Retirer de la vente',
          danger: true,
        }
        : {
          title: 'Remettre en vente ?',
          message: `« ${product.name} » sera de nouveau proposé aux caisses et aux commandes.`,
          confirmLabel: 'Remettre en vente',
          danger: false,
        },
      width: '480px',
      maxWidth: '95vw',
      panelClass: 'app-modal-panel',
      backdropClass: 'app-modal-backdrop',
    });

    if (!(await firstValueFrom(ref.closed))) return undefined;

    try {
      await firstValueFrom(this.api.changeRetired(product.id, retiring));
    } catch {
      return undefined; // l'erreur est déjà gérée par l'intercepteur HTTP
    }
    this.toasts.show(retiring ? `« ${product.name} » retiré de la vente.` : `« ${product.name} » remis en vente.`);
    return { changed: true };
  }

  /** Produit créé : on le dit, avec la suite habituelle à un tap. */
  private announceCreated(product: Product): void {
    const next: ToastAction[] = [];
    if (this.rights.canAddStock()) {
      next.push({ label: 'Ajouter du stock', run: () => void this.open({ action: 'addStock', product }) });
    }
    next.push({ label: 'Codes-barres', run: () => void this.open({ action: 'edit', product, tab: 'codes' }) });
    const stock = product.stockQuantity > 0 ? `, avec ${formatAmount(product.stockQuantity, product.unit)} en stock` : '';
    this.toasts.show(`Produit « ${product.name} » créé${stock}.`, next);
  }
  createProduct() {
    return  this.dialog.open<Product>(ProductCreateDialogComponent, {
      width:  '660px',
      maxWidth: '95vw',
      panelClass: 'app-modal-panel',
      backdropClass: 'app-modal-backdrop',
    }).closed;
  }
}
