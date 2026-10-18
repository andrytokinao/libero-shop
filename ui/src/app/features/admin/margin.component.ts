import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { EMPTY, catchError, switchMap } from 'rxjs';
import { apiResource } from '../../core/api/api-resource';
import { CostingApi } from '../../core/api/costing.api';
import { ProductDialogRequest, ProductRights } from '../../core/catalog/product-rights';
import { MarginReport, Product, StockValuation } from '../../core/models';
import { ProductStore } from '../../core/store/product.store';
import { isoDate } from '../../core/utils/date.util';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { ProductDialogsComponent } from '../../shared/components/product-dialogs.component';
import { ProductMenuComponent } from '../../shared/components/product-menu.component';
import { LoadStateComponent } from '../../shared/components/load-state.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/** The periods a manager actually asks about; anything else goes through the two dates. */
type Preset = 'today' | 'week' | 'month' | 'lastMonth' | 'custom';

interface Period {
  from: string;
  to: string;
}

function presetPeriod(preset: Exclude<Preset, 'custom'>): Period {
  const today = new Date();
  switch (preset) {
    case 'today':
      return { from: isoDate(today), to: isoDate(today) };
    case 'week': {
      const start = new Date(today);
      start.setDate(start.getDate() - 6);
      return { from: isoDate(start), to: isoDate(today) };
    }
    case 'month':
      return { from: isoDate(new Date(today.getFullYear(), today.getMonth(), 1)), to: isoDate(today) };
    case 'lastMonth':
      return {
        from: isoDate(new Date(today.getFullYear(), today.getMonth() - 1, 1)),
        to: isoDate(new Date(today.getFullYear(), today.getMonth(), 0)),
      };
  }
}

/**
 * The money side of the shop, in two halves that answer two different questions.
 *
 * <p>"What did we earn?" — the period half: turnover, what the goods sold had cost, and the
 * profit made. The costs are the ones the server froze on each sale at checkout, so the page shows
 * what the shop really earned on the day, not a re-computation at today's prices.
 *
 * <p>"What is on the shelves?" — the stock half: the money tied up in the stock at its average
 * cost, what it would bring if it all sold, and the profit still waiting in it. A snapshot, so it
 * does not follow the dates chosen above.
 *
 * <p>In both halves, goods whose cost was never recorded are shown apart and never folded into a
 * profit as if they had cost nothing.
 */
@Component({
  selector: 'app-margin',
  standalone: true,
  imports: [
    LoadStateComponent,
    FormsModule,
    DatePipe,
    KpiCardComponent,
    ProductDialogsComponent,
    ProductMenuComponent,
    AriaryPipe,
  ],
  template: `
    <!-- ================================================= what the period earned -->
    <div class="card period">
      <h2>Ventes et bénéfice</h2>
      <div class="spacer"></div>
      <label for="margin-preset">Période</label>
      <select
        id="margin-preset"
        class="field"
        [ngModel]="preset()"
        (ngModelChange)="choose($event)"
      >
        <option value="today">Aujourd'hui</option>
        <option value="week">7 derniers jours</option>
        <option value="month">Ce mois-ci</option>
        <option value="lastMonth">Mois précédent</option>
        <option value="custom">Dates au choix</option>
      </select>
      <input
        type="date"
        class="field"
        aria-label="Du"
        [ngModel]="period().from"
        (ngModelChange)="setDates($event, period().to)"
      />
      <span class="muted">au</span>
      <input
        type="date"
        class="field"
        aria-label="Au"
        [ngModel]="period().to"
        (ngModelChange)="setDates(period().from, $event)"
      />
    </div>

    @if (report(); as r) {
      <div class="grid g4" style="margin-top:16px;">
        <app-kpi-card
          label="Chiffre d'affaires"
          [value]="r.revenue | ariary"
          [hint]="'dont encaissé ' + (r.paidRevenue | ariary) + ' · reste dû ' + (r.revenue - r.paidRevenue | ariary)"
        />
        <app-kpi-card
          label="Coût d'achat des ventes"
          [value]="r.costOfGoodsSold | ariary"
          hint="au coût moyen du jour de chaque vente"
        />
        <app-kpi-card
          label="Bénéfice réalisé"
          [value]="r.grossMargin | ariary"
          [hint]="'du ' + (r.from | date: 'dd/MM') + ' au ' + (r.to | date: 'dd/MM')"
        />
        <app-kpi-card
          label="Taux de marge"
          [value]="percent(r.marginRate)"
          hint="bénéfice / prix de vente"
        />
      </div>

      @if (r.coveragePercent < 100) {
        <div class="card coverage">
          Le bénéfice couvre {{ r.coveragePercent }} % du chiffre d'affaires.
          {{ r.uncostedRevenue | ariary }} ont été vendus sans prix d'achat connu et ne sont pas
          comptés. Saisissez le prix d'achat à chaque approvisionnement pour compléter le calcul.
        </div>
      }

      <div class="card" style="margin-top:16px;">
        <h2>Bénéfice par produit <small>du plus fort au plus faible</small></h2>
        @if (r.byProduct.length) {
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Produit</th>
                  <th class="num">Unités</th>
                  <th class="num">Chiffre d'affaires</th>
                  <th class="num">Coût d'achat</th>
                  <th class="num">Bénéfice</th>
                  <th class="num">Taux</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (row of r.byProduct; track row.productId) {
                  <tr>
                    <td>
                      {{ row.name }}
                      @if (!row.fullyCosted) {
                        @if (rights.canEdit() && productOf(row.productId); as product) {
                          <!-- The fix is one field away: the configuration, cursor in the purchase price. A sale keeps
                               the cost known the day it was made, so this costs the next ones. -->
                          <button
                            type="button"
                            class="badge amber badge-btn"
                            title="Une partie de ces ventes n'a pas de prix d'achat. Le saisir maintenant compte pour les prochaines ventes ; celles déjà faites gardent le coût connu ce jour-là."
                            (click)="asked.set({ action: 'edit', product, focus: 'cost' })"
                          >
                            prix d'achat inconnu ›
                          </button>
                        } @else {
                          <span class="badge amber" title="Une partie des ventes n'a pas de prix d'achat connu">
                            partiel
                          </span>
                        }
                      }
                    </td>
                    <td class="num">{{ row.unitsSold }}</td>
                    <td class="num">{{ row.revenue | ariary }}</td>
                    <td class="num">{{ row.costOfGoodsSold | ariary }}</td>
                    <td class="num" [class.loss]="row.grossMargin < 0">{{ row.grossMargin | ariary }}</td>
                    <td class="num">{{ percent(row.marginRate) }}</td>
                    <td class="num">
                      @if (productOf(row.productId); as product) {
                        <app-product-menu [product]="product" (action)="asked.set($event)" />
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <div class="empty">Aucune vente sur cette période.</div>
        }
      </div>
    } @else {
      <div class="empty" style="margin-top:16px;">Chargement...</div>
    }

    <!-- ================================================= what the shelves hold -->
    <app-load-state [of]="valuationResource" what="la valeur du stock" />
    @if (valuation(); as v) {
      <div class="card" style="margin-top:24px;">
        <h2>
          Valeur du stock
          <small>
            aujourd'hui · {{ v.units }} unité(s), {{ v.references }} référence(s) — ne dépend pas
            de la période
          </small>
        </h2>
        <div class="grid g3">
          <app-kpi-card
            [flat]="true"
            label="Valeur d'achat du stock"
            [value]="v.costValue | ariary"
            hint="ce que le stock a coûté, au coût moyen"
          />
          <app-kpi-card
            [flat]="true"
            label="Valeur de vente du stock"
            [value]="v.saleValue | ariary"
            hint="vendu au détail, au prix actuel de l'unité de base"
          />
          <app-kpi-card
            [flat]="true"
            label="Bénéfice potentiel"
            [value]="v.potentialMargin | ariary"
            [hint]="'taux ' + percent(v.potentialMarginRate) + ' · réalisé une fois vendu'"
          />
        </div>

        @if (v.coveragePercent < 100) {
          <div class="coverage-inline">
            {{ v.uncostedUnits }} unité(s) n'ont jamais eu de prix d'achat : elles comptent dans la
            valeur de vente mais pas dans la valeur d'achat ni dans le bénéfice potentiel (couverture
            {{ v.coveragePercent }} %).
          </div>
        }

        @if (v.byCategory.length) {
          <div class="table-scroll">
            <table style="margin-top:12px;">
              <thead>
                <tr>
                  <th>Catégorie</th>
                  <th class="num">Unités</th>
                  <th class="num">Valeur d'achat</th>
                  <th class="num">Valeur de vente</th>
                  <th class="num">Bénéfice potentiel</th>
                  <th class="num">Taux</th>
                </tr>
              </thead>
              <tbody>
                @for (row of v.byCategory; track row.categoryId) {
                  <tr>
                    <td>
                      {{ row.path }}
                      @if (row.uncostedUnits > 0) {
                        <span
                          class="badge amber"
                          [title]="row.uncostedUnits + ' unité(s) sans prix d’achat'"
                        >
                          partiel
                        </span>
                      }
                    </td>
                    <td class="num">{{ row.units }}</td>
                    <td class="num">{{ row.costValue | ariary }}</td>
                    <td class="num">{{ row.saleValue | ariary }}</td>
                    <td class="num" [class.loss]="row.potentialMargin < 0">
                      {{ row.potentialMargin | ariary }}
                    </td>
                    <td class="num">{{ percent(row.potentialMarginRate) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <div class="empty">Aucun produit en stock.</div>
        }
      </div>
    }

    <app-product-dialogs [request]="asked()" (changed)="onChanged()" (closed)="asked.set(null)" />
  `,
  styles: `
    .badge-btn {
      border: 0;
      font-family: inherit;
      cursor: pointer;

      &:hover {
        text-decoration: underline;
      }
    }

    .period {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;

      h2 {
        margin: 0;
      }

      .spacer {
        flex: 1;
      }

      label {
        font-size: 11.5px;
        color: var(--ink-soft);
        font-weight: 600;
      }
    }

    .coverage {
      margin-top: 16px;
      font-size: 12.5px;
      color: var(--amber);
    }

    .coverage-inline {
      margin-top: 10px;
      font-size: 12.5px;
      color: var(--amber);
    }

    .loss {
      color: var(--red);
      font-weight: 600;
    }
  `,
})
export class MarginComponent {
  private readonly costing = inject(CostingApi);
  protected readonly rights = inject(ProductRights);

  protected readonly preset = signal<Preset>('month');
  protected readonly period = signal<Period>(presetPeriod('month'));

  /**
   * Null while the first answer is on its way. A refused period — the end before the start —
   * emits nothing and so keeps the last report on screen: the error interceptor has already
   * said why.
   */
  protected readonly report = toSignal<MarginReport | null>(
    toObservable(this.period).pipe(
      switchMap(({ from, to }) => this.costing.margin(from, to).pipe(catchError(() => EMPTY))),
    ),
    { initialValue: null },
  );

  /** Loaded once: the stock does not depend on the period chosen. */
  protected readonly valuationResource = apiResource<StockValuation | null>(null, () =>
    this.costing.stockValuation(),
  );
  protected readonly valuation = this.valuationResource.value;

  /** What a product's menu asked for, or null while no dialog is open. */
  protected readonly asked = signal<ProductDialogRequest | null>(null);
  /** The products themselves, which the menus and dialogs open on. */
  private readonly products = inject(ProductStore).list().value;
  private readonly productsById = computed(
    () => new Map(this.products().map((product) => [product.id, product])),
  );

  protected productOf(productId: number): Product | null {
    return this.productsById().get(productId) ?? null;
  }

  /**
   * A purchase price typed or goods received: both halves are the server's to work out again.
   * The period is handed back as a new object so the report is asked for once more.
   */
  protected onChanged(): void {
    this.period.set({ ...this.period() });
    this.valuationResource.reload();
  }

  protected choose(preset: Preset): void {
    this.preset.set(preset);
    if (preset !== 'custom') {
      this.period.set(presetPeriod(preset));
    }
  }

  protected setDates(from: string, to: string): void {
    if (!from || !to) {
      return;
    }
    this.preset.set('custom');
    this.period.set({ from, to });
  }

  /** "18,4 %", or a dash when there is no rate — nothing sold, or nothing costed. */
  protected percent(rate: number | null): string {
    return rate === null
      ? '—'
      : `${rate.toLocaleString('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} %`;
  }
}
