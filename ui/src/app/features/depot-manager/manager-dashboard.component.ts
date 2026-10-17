import { Component, inject, signal } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { DashboardApi } from '../../core/api/dashboard.api';
import { ProductDialogRequest } from '../../core/catalog/product-rights';
import { StockDashboard } from '../../core/models';
import { reloadOnStockChange } from '../../core/realtime/reload-on';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { ProductDialogsComponent } from '../../shared/components/product-dialogs.component';
import { StockTableComponent } from '../../shared/components/stock-table.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

const EMPTY: StockDashboard = {
  stockValue: 0,
  referenceCount: 0,
  unitsInStock: 0,
  lowStockCount: 0,
  suppliesLastWeek: 0,
  unitsSuppliedLastWeek: 0,
  deliveredInvoices: 0,
  toRestock: [],
};

@Component({
  selector: 'app-manager-dashboard',
  standalone: true,
  imports: [KpiCardComponent, ProductDialogsComponent, StockTableComponent, AriaryPipe],
  template: `
    <div class="grid g4">
      <app-kpi-card
        label="Valeur totale du stock"
        [value]="data().stockValue | ariary"
        [hint]="data().referenceCount + ' références, ' + data().unitsInStock + ' unités'"
      />
      <app-kpi-card
        label="Références en alerte"
        [value]="data().lowStockCount"
        hint="sous leur seuil d'alerte"
        link="/gestion-depot/stock"
        [linkQuery]="{ alertes: '1' }"
      />
      <app-kpi-card
        label="Approvisionnements (7 j)"
        link="/gestion-depot/approvisionnement"
        [value]="data().suppliesLastWeek"
        [hint]="data().unitsSuppliedLastWeek + ' unités reçues'"
      />
      <app-kpi-card label="Commandes livrées" [value]="data().deliveredInvoices" />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Produits à réapprovisionner en priorité
        <small>triés du stock le plus faible au plus élevé</small>
      </h2>
      <app-stock-table
        [state]="resource"
        [products]="data().toRestock"
        [manage]="true"
        emptyMessage="Aucun produit en alerte — le stock est sain."
        (action)="asked.set($event)"
      />
    </div>

    <app-product-dialogs [request]="asked()" (changed)="resource.reload()" (closed)="asked.set(null)" />
  `,
})
export class ManagerDashboardComponent {
  private readonly api = inject(DashboardApi);
  protected readonly resource = apiResource(EMPTY, () => this.api.stock());
  protected readonly data = this.resource.value;

  /** What a product's menu asked for — restocking the one in alert, from here — or null. */
  protected readonly asked = signal<ProductDialogRequest | null>(null);

  constructor() {
    // Sold, received, cancelled: the alerts and the value follow every stock movement.
    reloadOnStockChange(this.resource);
  }
}
