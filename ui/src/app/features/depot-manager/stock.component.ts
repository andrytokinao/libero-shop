import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { looksLikeCode } from '../../core/catalog/product-pick';
import { ProductDialogRequest, ProductRights } from '../../core/catalog/product-rights';
import { ToastService } from '../../core/services/toast.service';
import { CategoryNode } from '../../core/models';
import { ProductStore } from '../../core/store/product.store';
import { CategoryPickerComponent } from '../../shared/components/category-picker.component';
import { CameraScanButtonComponent } from '../../shared/components/camera-scan-button.component';
import { DeliveryDialogComponent } from '../../shared/components/delivery-dialog.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { ProductCardComponent } from '../../shared/components/product-card.component';
import { ProductDialogsComponent } from '../../shared/components/product-dialogs.component';
import { StockTableComponent } from '../../shared/components/stock-table.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';
import { ProductImportComponent } from './product-import.component';

/** A table, which says the most per line, or cards, which a thumb reads better. */
type StockView = 'list' | 'cards';

const STOCK_VIEW_STORAGE_KEY = 'liberoshop.stock.view';

@Component({
  selector: 'app-stock',
  standalone: true,
  imports: [
    FormsModule,
    CategoryPickerComponent,
    KpiCardComponent,
    ProductCardComponent,
    ProductDialogsComponent,
    ProductImportComponent,
    StockTableComponent,
    CameraScanButtonComponent,
    DeliveryDialogComponent,
    AriaryPipe,
  ],
  template: `
    <div class="grid g3">
      <app-kpi-card label="Valeur du stock affiché" [value]="visibleValue() | ariary" />
      <app-kpi-card label="Références affichées" [value]="products().length" />
      <app-kpi-card label="Unités en stock" [value]="visibleUnits()" />
    </div>

    <div class="card" style="margin-top:16px;">
      <div class="head-row">
        <h2>Stock du dépôt</h2>
        <div class="st-actions">
          <!-- A delivery note with several products: one form, one save. -->
          @if (rights.canAddStock()) {
            <button class="btn small" type="button" (click)="receiving.set(true)">＋ Réception</button>
          }
          <!-- One product at a time, by hand: no file needed. -->
          @if (rights.canEdit()) {
            <button class="btn small" type="button" (click)="asked.set({ action: 'create' })">
              ＋ Nouveau produit
            </button>
          }
          <app-product-import (imported)="onChanged()" />
        </div>
      </div>
      <div class="form-row">
        <div class="fld" style="flex:1; max-width:320px;">
          <label for="stock-search">Recherche</label>
          <!-- A scanner types the code and presses Entrée; the camera reads it on a phone. Either way
               the product found opens with its menu, ready for what is to be done with it. -->
          <div class="st-search">
          <input
            id="stock-search"
            type="text"
            placeholder="Nom, catégorie ou code-barres"
            [ngModel]="search()"
            (ngModelChange)="onSearch($event)"
            (focus)="$any($event.target).select()"
            (keydown.enter)="scan(search())"
          />
          <app-camera-scan-button (scanned)="scan($event)" />
          </div>
        </div>
        <div class="fld">
          <label for="stock-category">Catégorie</label>
          <!-- Picking a parent rayon brings its sub-rayons with it; the server decides that,
               so this screen and the cash desk's product search agree on what "Boissons" means. -->
          <app-category-picker
            fieldId="stock-category"
            [categories]="categories()"
            [value]="categoryId()"
            (valueChange)="onCategory($event)"
          />
        </div>
        <div class="fld">
          <label for="stock-alert">Filtre</label>
          <select
            id="stock-alert"
            class="field"
            [ngModel]="onlyLowStock()"
            (ngModelChange)="onLowStock($event)"
          >
            <option [ngValue]="false">Tout le stock</option>
            <option [ngValue]="true">Alertes uniquement</option>
          </select>
        </div>
        <div class="st-view" role="radiogroup" aria-label="Affichage des produits">
          <button
            type="button"
            role="radio"
            class="btn small"
            [class.ghost]="view() !== 'list'"
            [attr.aria-checked]="view() === 'list'"
            (click)="choose('list')"
          >
            Liste
          </button>
          <button
            type="button"
            role="radio"
            class="btn small"
            [class.ghost]="view() !== 'cards'"
            [attr.aria-checked]="view() === 'cards'"
            (click)="choose('cards')"
          >
            Cartes
          </button>
        </div>
      </div>
      @if (!products().length && resource.loaded()) {
        <!-- An empty list says what to do next, with the button to do it. -->
        <div class="empty">
          @if (filtered()) {
            Aucun produit ne correspond aux filtres.
            <div class="empty-actions">
              <button class="btn small ghost" type="button" (click)="clearFilters()">Effacer les filtres</button>
            </div>
          } @else {
            Aucun produit pour l'instant.
            <div class="empty-actions">
              @if (rights.canEdit()) {
                <button class="btn small" type="button" (click)="asked.set({ action: 'create' })">
                  ＋ Nouveau produit
                </button>
              }
              <app-product-import (imported)="onChanged()" />
            </div>
          }
        </div>
      } @else if (view() === 'list') {
        <app-stock-table
          [state]="resource"
          [products]="products()"
          [manage]="true"
          [openMenuOf]="openMenuOf()"
          emptyMessage="Aucun produit ne correspond aux filtres."
          (action)="asked.set($event)"
        />
      } @else {
        <div class="st-cards">
          @for (product of products(); track product.id) {
            <app-product-card
              [product]="product"
              [manage]="true"
              [openMenu]="product.id === openMenuOf()"
              (action)="asked.set($event)"
            />
          } @empty {
            <div class="empty">Aucun produit ne correspond aux filtres.</div>
          }
        </div>
      }
    </div>

    <app-product-dialogs [request]="asked()" (changed)="onChanged()" (closed)="asked.set(null)" />

    @if (receiving()) {
      <app-delivery-dialog (changed)="onChanged()" (closed)="receiving.set(false)" />
    }
  `,
  styles: `
    .st-search {
      display: flex;
      gap: 6px;

      input {
        flex: 1;
        min-width: 0;
      }

      app-camera-scan-button:empty {
        display: none;
      }
    }

    .st-actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      justify-content: flex-end;
    }

    .st-view {
      display: flex;
      gap: 4px;
      margin-left: auto;
    }

    .st-cards {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
      gap: 10px;

      .empty {
        grid-column: 1 / -1;
      }
    }
  `,
})
export class StockComponent {
  private readonly api = inject(CatalogApi);
  protected readonly rights = inject(ProductRights);

  protected readonly search = signal('');
  protected readonly categoryId = signal<number | null>(null);
  /** "Références en alerte" on the dashboard opens this list with the filter on (?alertes=1). */
  protected readonly onlyLowStock = signal(inject(ActivatedRoute).snapshot.queryParamMap.get('alertes') === '1');
  protected readonly view = signal<StockView>(storedView());

  /** What a product's menu or "Nouveau produit" asked for, or null while no dialog is open. */
  protected readonly asked = signal<ProductDialogRequest | null>(null);

  // The three filters are applied by the server, so the browser never holds the
  // whole catalogue just to narrow it down; the store keeps the figures current — a sale at
  // the till lowers the count here.
  protected readonly resource = inject(ProductStore).list(() => ({
    search: this.search(),
    categoryId: this.categoryId(),
    lowStockOnly: this.onlyLowStock(),
  }));
  private readonly categoryResource = apiResource<CategoryNode[]>([], () => this.api.categories());

  protected readonly products = this.resource.value;
  protected readonly categories = this.categoryResource.value;

  protected readonly visibleValue = computed(() =>
    this.products().reduce((total, p) => total + p.stockValue, 0),
  );

  protected readonly visibleUnits = computed(() =>
    this.products().reduce((total, p) => total + p.stockQuantity, 0),
  );

  protected onSearch(value: string): void {
    this.search.set(value);
    this.openMenuOf.set(null);
    this.resource.reload();
  }

  /** The delivery note open. */
  protected readonly receiving = signal(false);

  /** The product a scanned code found: its menu opens in the list. */
  protected readonly openMenuOf = signal<number | null>(null);
  private readonly toasts = inject(ToastService);

  /**
   * A code scanned or typed then Entrée: the list narrows to its product, whose menu opens. A
   * carton's code finds its product too; the list then shows it by name.
   */
  protected scan(text: string): void {
    const code = text.trim();
    if (!looksLikeCode(code)) {
      return;
    }
    this.api.productByCode(code).subscribe((product) => {
      if (!product) {
        this.toasts.show(`Aucun produit ne porte le code ${code}.`);
        return;
      }
      this.search.set(product.barcode === code ? code : product.name);
      this.resource.reload();
      this.openMenuOf.set(product.id);
    });
  }

  protected onCategory(categoryId: number | null): void {
    this.categoryId.set(categoryId);
    this.resource.reload();
  }

  protected onLowStock(onlyLowStock: boolean): void {
    this.onlyLowStock.set(onlyLowStock);
    this.resource.reload();
  }

  /** A search, a rayon or "Alertes uniquement" narrows the list: empty may just mean too narrow. */
  protected readonly filtered = computed(
    () => this.search().trim() !== '' || this.categoryId() !== null || this.onlyLowStock(),
  );

  protected clearFilters(): void {
    this.search.set('');
    this.categoryId.set(null);
    this.onlyLowStock.set(false);
    this.resource.reload();
  }

  /** Kept on this device: the phone behind the counter and the office laptop each keep theirs. */
  protected choose(view: StockView): void {
    this.view.set(view);
    try {
      localStorage.setItem(STOCK_VIEW_STORAGE_KEY, view);
    } catch {
      // Private browsing: the choice lasts as long as the screen.
    }
  }

  /**
   * Both lists after an import, a creation or an edit: any of them may add references, move
   * quantities or rename a unit, and an import may have created the rayons its file named —
   * which the filter above has to offer straight away.
   */
  protected onChanged(): void {
    this.resource.reload();
    this.categoryResource.reload();
  }
}

function storedView(): StockView {
  try {
    return localStorage.getItem(STOCK_VIEW_STORAGE_KEY) === 'cards' ? 'cards' : 'list';
  } catch {
    return 'list';
  }
}
