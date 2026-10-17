import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { ProductDialogRequest, ProductRights } from '../../core/catalog/product-rights';
import { CategoryNode } from '../../core/models';
import { ProductStore } from '../../core/store/product.store';
import { CategoryPickerComponent } from '../../shared/components/category-picker.component';
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
        <div class="fld" style="flex:1; max-width:280px;">
          <label for="stock-search">Recherche</label>
          <input
            id="stock-search"
            type="text"
            placeholder="Nom, catégorie ou code-barres"
            [ngModel]="search()"
            (ngModelChange)="onSearch($event)"
          />
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
      @if (!products().length && !resource.loading()) {
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
          [products]="products()"
          [manage]="true"
          emptyMessage="Aucun produit ne correspond aux filtres."
          (action)="asked.set($event)"
        />
      } @else {
        <div class="st-cards">
          @for (product of products(); track product.id) {
            <app-product-card [product]="product" [manage]="true" (action)="asked.set($event)" />
          } @empty {
            <div class="empty">Aucun produit ne correspond aux filtres.</div>
          }
        </div>
      }
    </div>

    <app-product-dialogs [request]="asked()" (changed)="onChanged()" (closed)="asked.set(null)" />
  `,
  styles: `
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
  protected readonly onlyLowStock = signal(false);
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
    this.resource.reload();
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
