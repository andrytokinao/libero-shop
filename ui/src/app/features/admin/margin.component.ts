import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { EMPTY, catchError, switchMap } from 'rxjs';
import { CostingApi } from '../../core/api/costing.api';
import { MarginReport } from '../../core/models';
import { isoDate } from '../../core/utils/date.util';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
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
 * Gross margin over a period: sold, cost, earned.
 *
 * <p>Every figure comes from the server, which froze each sale's cost at checkout — so the page
 * shows what the shop really earned on the day, not a re-computation at today's prices. Sales of
 * goods whose cost was never recorded are shown apart as "non valorisé", never folded into the
 * margin as if they had cost nothing.
 */
@Component({
  selector: 'app-margin',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, KpiCardComponent, AriaryPipe],
  template: `
    <div class="card period">
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
          label="Ventes"
          [value]="r.revenue | ariary"
          [hint]="'du ' + (r.from | date: 'dd/MM') + ' au ' + (r.to | date: 'dd/MM')"
        />
        <app-kpi-card label="Coût des ventes" [value]="r.costOfGoodsSold | ariary" hint="au coût moyen du jour de vente" />
        <app-kpi-card label="Marge brute" [value]="r.grossMargin | ariary" />
        <app-kpi-card
          label="Taux de marge"
          [value]="r.marginRate === null ? '—' : (r.marginRate | number: '1.1-1') + ' %'"
          hint="marge / prix de vente"
        />
      </div>

      @if (r.coveragePercent < 100) {
        <div class="card coverage">
          La marge couvre {{ r.coveragePercent }} % des ventes.
          {{ r.uncostedRevenue | ariary }} ont été vendus sans prix d'achat connu : ils ne sont pas
          comptés dans la marge. Saisissez le prix d'achat à chaque approvisionnement pour
          compléter le calcul.
        </div>
      }

      <div class="card" style="margin-top:16px;">
        <h2>Marge par produit <small>de la plus forte à la plus faible</small></h2>
        @if (r.byProduct.length) {
          <table>
            <thead>
              <tr>
                <th>Produit</th>
                <th class="num">Unités</th>
                <th class="num">Ventes</th>
                <th class="num">Coût</th>
                <th class="num">Marge</th>
                <th class="num">Taux</th>
              </tr>
            </thead>
            <tbody>
              @for (row of r.byProduct; track row.productId) {
                <tr>
                  <td>
                    {{ row.name }}
                    @if (!row.fullyCosted) {
                      <span class="badge amber" title="Une partie des ventes n'a pas de prix d'achat connu">
                        partiel
                      </span>
                    }
                  </td>
                  <td class="num">{{ row.unitsSold }}</td>
                  <td class="num">{{ row.revenue | ariary }}</td>
                  <td class="num">{{ row.costOfGoodsSold | ariary }}</td>
                  <td class="num" [class.loss]="row.grossMargin < 0">{{ row.grossMargin | ariary }}</td>
                  <td class="num">
                    {{ row.marginRate === null ? '—' : (row.marginRate | number: '1.1-1') + ' %' }}
                  </td>
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <div class="empty">Aucune vente sur cette période.</div>
        }
      </div>
    } @else {
      <div class="empty" style="margin-top:16px;">Chargement...</div>
    }
  `,
  styles: `
    .period {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;

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

    .loss {
      color: var(--red);
      font-weight: 600;
    }
  `,
})
export class MarginComponent {
  private readonly costing = inject(CostingApi);

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
}
