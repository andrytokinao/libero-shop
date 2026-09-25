import { Component, computed, inject } from '@angular/core';
import { ShopStore } from '../../core/services/shop-store.service';
import { isToday } from '../../core/utils/date.util';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { RevenueBarsComponent } from '../../shared/components/revenue-bars.component';
import { StockTableComponent } from '../../shared/components/stock-table.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-admin-overview',
  standalone: true,
  imports: [KpiCardComponent, RevenueBarsComponent, StockTableComponent, AriaryPipe],
  template: `
    <div class="grid g4">
      <app-kpi-card
        label="Chiffre d'affaires du jour"
        [value]="store.revenueToday() | ariary"
        hint="toutes caisses confondues"
      />
      <app-kpi-card
        label="Valeur du stock"
        [value]="store.stockValue() | ariary"
        [hint]="store.products().length + ' références'"
      />
      <app-kpi-card
        label="Factures non payées"
        [value]="store.unpaidInvoices().length"
        [hint]="unpaidAmount() | ariary"
      />
      <app-kpi-card
        label="Commandes en attente de remise"
        [value]="store.pendingDeliveries().length"
      />
    </div>

    <div class="grid g2" style="margin-top:16px;">
      <div class="card">
        <h2>
          Chiffre d'affaires par vendeur
          <small>mis à jour à chaque vente encaissée</small>
        </h2>
        <app-revenue-bars [data]="store.revenueBySeller()" />
      </div>
      <div class="card">
        <h2>Produits en alerte stock</h2>
        <app-stock-table
          [products]="store.lowStockProducts()"
          emptyMessage="Aucun produit en alerte."
        />
      </div>
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Suivi des espèces encaissées au dépôt
        <small>contrôle de la remise à la caisse</small>
      </h2>
      <div class="grid g3">
        <app-kpi-card
          [flat]="true"
          label="Encore en main des agents"
          [value]="cashInHandTotal() | ariary"
          [hint]="store.depotCashInHand().length + ' encaissement(s)'"
        />
        <app-kpi-card
          [flat]="true"
          label="Versements en attente de confirmation"
          [value]="pendingTotal() | ariary"
          [hint]="store.pendingRemittances().length + ' bordereau(x)'"
        />
        <app-kpi-card
          [flat]="true"
          label="Versements confirmés aujourd'hui"
          [value]="confirmedTodayTotal() | ariary"
        />
      </div>
    </div>
  `,
})
export class AdminOverviewComponent {
  protected readonly store = inject(ShopStore);

  protected readonly unpaidAmount = computed(() =>
    this.store.unpaidInvoices().reduce((total, i) => total + i.sale.totalAmount, 0),
  );

  protected readonly cashInHandTotal = computed(() =>
    ShopStore.total(this.store.depotCashInHand()),
  );

  protected readonly pendingTotal = computed(() =>
    ShopStore.total(this.store.pendingRemittances()),
  );

  protected readonly confirmedTodayTotal = computed(() =>
    ShopStore.total(this.store.confirmedRemittances().filter((r) => isToday(r.remittanceDate))),
  );
}
