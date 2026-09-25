import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { Category, Product } from '../../core/models';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { StockTableComponent } from '../../shared/components/stock-table.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-stock',
  standalone: true,
  imports: [FormsModule, KpiCardComponent, StockTableComponent, AriaryPipe],
  template: `
    <div class="grid g3">
      <app-kpi-card label="Valeur du stock affiché" [value]="visibleValue() | ariary" />
      <app-kpi-card label="Références affichées" [value]="products().length" />
      <app-kpi-card label="Unités en stock" [value]="visibleUnits()" />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Stock du dépôt</h2>
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
          <select
            id="stock-category"
            class="field"
            [ngModel]="categoryId()"
            (ngModelChange)="onCategory($event)"
          >
            <option [ngValue]="null">Toutes les catégories</option>
            @for (category of categories(); track category.id) {
              <option [ngValue]="category.id">{{ category.name }}</option>
            }
          </select>
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
      </div>
      <app-stock-table
        [products]="products()"
        emptyMessage="Aucun produit ne correspond aux filtres."
      />
    </div>
  `,
})
export class StockComponent {
  private readonly api = inject(CatalogApi);

  protected readonly search = signal('');
  protected readonly categoryId = signal<number | null>(null);
  protected readonly onlyLowStock = signal(false);

  // The three filters are applied by the server, so the browser never holds the
  // whole catalogue just to narrow it down.
  private readonly resource = apiResource<Product[]>([], () =>
    this.api.products({
      search: this.search(),
      categoryId: this.categoryId(),
      lowStockOnly: this.onlyLowStock(),
    }),
  );
  private readonly categoryResource = apiResource<Category[]>([], () => this.api.categories());

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
}
