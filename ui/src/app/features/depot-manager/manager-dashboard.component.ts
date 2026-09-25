import { Component, computed, inject } from '@angular/core';
import { ShopStore } from '../../core/services/shop-store.service';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { StockTableComponent } from '../../shared/components/stock-table.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-manager-dashboard',
  standalone: true,
  imports: [KpiCardComponent, StockTableComponent, AriaryPipe],
  template: `
    <div class="grid g4">
      <app-kpi-card
        label="Valeur totale du stock"
        [value]="store.stockValue() | ariary"
        [hint]="store.products().length + ' références'"
      />
      <app-kpi-card
        label="Références en alerte"
        [value]="store.lowStockProducts().length"
        hint="stock inférieur à 10"
      />
      <app-kpi-card
        label="Approvisionnements (7 j)"
        [value]="store.suppliesLastWeek().length"
        [hint]="unitsSuppliedLastWeek() + ' unités reçues'"
      />
      <app-kpi-card label="Commandes livrées" [value]="store.deliveredInvoices().length" />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Produits à réapprovisionner en priorité
        <small>triés du stock le plus faible au plus élevé</small>
      </h2>
      <app-stock-table
        [products]="toRestock()"
        emptyMessage="Aucun produit en alerte — le stock est sain."
      />
    </div>
  `,
})
export class ManagerDashboardComponent {
  protected readonly store = inject(ShopStore);

  protected readonly toRestock = computed(() =>
    [...this.store.lowStockProducts()].sort((a, b) => a.stockQuantity - b.stockQuantity),
  );

  protected readonly unitsSuppliedLastWeek = computed(() =>
    this.store.suppliesLastWeek().reduce((total, s) => total + s.quantity, 0),
  );
}
