import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ShopStore } from '../../core/services/shop-store.service';
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
      <app-kpi-card label="Références affichées" [value]="visibleProducts().length" />
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
            placeholder="Nom ou code-barres"
            [ngModel]="search()"
            (ngModelChange)="search.set($event)"
          />
        </div>
        <div class="fld">
          <label for="stock-category">Catégorie</label>
          <select
            id="stock-category"
            class="field"
            [ngModel]="categoryId()"
            (ngModelChange)="categoryId.set($event)"
          >
            <option [ngValue]="null">Toutes les catégories</option>
            @for (category of store.categories(); track category.id) {
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
            (ngModelChange)="onlyLowStock.set($event)"
          >
            <option [ngValue]="false">Tout le stock</option>
            <option [ngValue]="true">Alertes uniquement</option>
          </select>
        </div>
      </div>
      <app-stock-table
        [products]="visibleProducts()"
        emptyMessage="Aucun produit ne correspond aux filtres."
      />
    </div>
  `,
})
export class StockComponent {
  protected readonly store = inject(ShopStore);

  protected readonly search = signal('');
  protected readonly categoryId = signal<number | null>(null);
  protected readonly onlyLowStock = signal(false);

  protected readonly visibleProducts = computed(() => {
    const term = this.search().trim().toLowerCase();
    const category = this.categoryId();
    const lowStockIds = new Set(this.store.lowStockProducts().map((p) => p.id));

    return this.store.products().filter((product) => {
      if (category !== null && product.category?.id !== category) {
        return false;
      }
      if (this.onlyLowStock() && !lowStockIds.has(product.id)) {
        return false;
      }
      if (!term) {
        return true;
      }
      return [product.name, product.barcode ?? ''].some((field) =>
        field.toLowerCase().includes(term),
      );
    });
  });

  protected readonly visibleValue = computed(() =>
    this.visibleProducts().reduce((total, p) => total + p.price * p.stockQuantity, 0),
  );

  protected readonly visibleUnits = computed(() =>
    this.visibleProducts().reduce((total, p) => total + p.stockQuantity, 0),
  );
}
