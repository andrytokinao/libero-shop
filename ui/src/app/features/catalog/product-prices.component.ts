import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { CostingApi } from '../../core/api/costing.api';
import { findByCode, looksLikeCode, searchKey } from '../../core/catalog/product-pick';
import { ProductDialogRequest } from '../../core/catalog/product-rights';
import { Product, ProductCost } from '../../core/models';
import { ProductStore } from '../../core/store/product.store';
import { CameraScanButtonComponent } from '../../shared/components/camera-scan-button.component';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { ProductDialogsComponent } from '../../shared/components/product-dialogs.component';
import { ProductMenuComponent } from '../../shared/components/product-menu.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/** Which products the table keeps. */
type PriceFilter = 'all' | 'uncosted' | 'loss';

/**
 * Every product's configuration on one page: each unit it sells in, what it sells for, what it
 * cost the shop and the profit left on one of it — the page to read before changing a price.
 *
 * <p>One row per unit, the product named once beside them, with the same "⋮" as on the
 * stock screen: its configuration, where prices, purchase price and units are corrected, and the
 * stock actions the account may take. The figures are the server's, worked out from the exact average cost, so
 * a tonne reads back at the price it was typed at.
 *
 * <p>Shown to the two roles that read purchase prices — the stock manager and the administrator
 * — and the actions follow {@link ProductRights}: only the stock manager receives goods.
 */
@Component({
  selector: 'app-product-prices',
  standalone: true,
  imports: [
    FormsModule,
    AriaryPipe,
    KpiCardComponent,
    ProductDialogsComponent,
    ProductMenuComponent,
    CameraScanButtonComponent,
  ],
  template: `
    <div class="grid g3">
      <app-kpi-card label="Produits" [value]="costs().length" />
      <app-kpi-card
        label="Sans prix d'entrée"
        [value]="uncosted()"
        hint="bénéfice inconnu tant qu'il manque"
      />
      <app-kpi-card label="Vendus à perte" [value]="atLoss()" hint="au moins une unité sous son coût" />
    </div>

    <div class="card" style="margin-top:16px;">
      <div class="head-row">
        <h2>Prix et unités</h2>
      </div>
      <p class="muted pp-intro">
        Pour chaque unité : le prix de vente, le prix d'entrée (calculé depuis le prix saisi dans
        l'unité d'achat, marquée « achat ») et le bénéfice sur une unité vendue.
      </p>
      <div class="form-row">
        <div class="fld" style="flex:1; max-width:280px;">
          <label for="pp-search">Recherche</label>
          <!-- A name, or a code from the scanner or the camera: the product it designates is shown
               alone, with its menu open. -->
          <div class="pp-search">
            <input
              id="pp-search"
              type="text"
              placeholder="Nom du produit ou code-barres"
              [ngModel]="search()"
              (ngModelChange)="search.set($event)"
              (focus)="$any($event.target).select()"
              (keydown.enter)="$event.preventDefault()"
            />
            <app-camera-scan-button (scanned)="search.set($event)" />
          </div>
        </div>
        <div class="fld">
          <label for="pp-filter">Filtre</label>
          <select id="pp-filter" class="field" [ngModel]="filter()" (ngModelChange)="filter.set($event)">
            <option value="all">Tous les produits</option>
            <option value="uncosted">Sans prix d'entrée</option>
            <option value="loss">Vendus à perte</option>
          </select>
        </div>
      </div>

      <div class="table-scroll">
        <table class="pp-table">
          <thead>
            <tr>
              <th>Produit</th>
              <th>Unité</th>
              <th class="num">Contient</th>
              <th class="num">Prix de vente</th>
              <th class="num">Prix d'entrée</th>
              <th class="num">Bénéfice / unité</th>
              <th class="num">Marge</th>
              <th></th>
            </tr>
          </thead>
          @for (cost of shown(); track cost.productId) {
            <tbody>
              @for (unit of cost.units; track unit.packagingId; let first = $first) {
                <tr>
                  @if (first) {
                    <td class="pp-name" [attr.rowspan]="cost.units.length">{{ cost.name }}</td>
                  }
                  <td>
                    {{ unit.label ?? 'unité' }}
                    @if (unit.packagingId === cost.purchaseUnitId) {
                      <span class="badge grey">achat</span>
                    }
                  </td>
                  <td class="num muted">
                    @if (unit.packagingId !== null) {
                      {{ formatFactor(unit.factor) }} {{ cost.unit ?? 'unité' }}
                    }
                  </td>
                  <td class="num">{{ unit.salePrice | ariary }}</td>
                  @if (unit.cost !== null) {
                    <td class="num">{{ unit.cost | ariary }}</td>
                    <td class="num" [class.pp-loss]="unit.margin! < 0">{{ unit.margin | ariary }}</td>
                    <td class="num" [class.pp-loss]="unit.margin! < 0">
                      {{ unit.marginRate === null ? '—' : formatRate(unit.marginRate) }}
                    </td>
                  } @else {
                    <td class="num"><span class="badge amber">à saisir</span></td>
                    <td class="num muted">—</td>
                    <td class="num muted">—</td>
                  }
                  @if (first) {
                    <td class="pp-actions" [attr.rowspan]="cost.units.length">
                      @if (productOf(cost); as product) {
                        <app-product-menu
                          [product]="product"
                          [openNow]="product.id === scanned()?.id"
                          (action)="asked.set($event)"
                        />
                      }
                    </td>
                  }
                </tr>
              }
            </tbody>
          } @empty {
            <tbody>
              <tr>
                <td colspan="8" class="empty">
                  {{ resource.loading() ? 'Chargement…' : 'Aucun produit ne correspond.' }}
                </td>
              </tr>
            </tbody>
          }
        </table>
      </div>
    </div>

    <app-product-dialogs [request]="asked()" (changed)="onChanged()" (closed)="asked.set(null)" />
  `,
  styles: `
    .pp-search {
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

    .pp-intro {
      font-size: 12.5px;
      margin: 0 0 12px;
    }

    .pp-table tbody + tbody {
      border-top: 2px solid var(--line);
    }

    .pp-name {
      font-weight: 600;
      vertical-align: top;
    }

    .pp-actions {
      vertical-align: top;
      white-space: nowrap;
      text-align: right;

      .btn + .btn {
        margin-left: 6px;
      }
    }

    .pp-loss {
      color: var(--red);
    }
  `,
})
export class ProductPricesComponent {
  private readonly costing = inject(CostingApi);

  protected readonly search = signal('');
  protected readonly filter = signal<PriceFilter>('all');
  /** What a button asked for, or null while no dialog is open. */
  protected readonly asked = signal<ProductDialogRequest | null>(null);

  protected readonly resource = apiResource<ProductCost[]>([], () => this.costing.productCosts());
  protected readonly costs = this.resource.value;
  /** The products themselves, which the dialogs open on: units, stock, kept current. */
  private readonly products = inject(ProductStore).list().value;
  private readonly productsById = computed(
    () => new Map(this.products().map((product) => [product.id, product])),
  );

  protected readonly uncosted = computed(
    () => this.costs().filter((cost) => cost.units[0]?.cost === null).length,
  );
  protected readonly atLoss = computed(() => this.costs().filter(isAtLoss).length);

  /** The product a code in the search designates — its own or a carton's — or null. */
  protected readonly scanned = computed(() =>
    looksLikeCode(this.search()) ? (findByCode(this.products(), this.search())?.product ?? null) : null,
  );

  protected readonly shown = computed(() => {
    const code = looksLikeCode(this.search());
    const scanned = this.scanned();
    const words = code ? '' : searchKey(this.search());
    const filter = this.filter();
    return this.costs().filter(
      (cost) =>
        (!code || cost.productId === scanned?.id) &&
        (!words || searchKey(cost.name).includes(words)) &&
        (filter === 'all' ||
          (filter === 'uncosted' && cost.units[0]?.cost === null) ||
          (filter === 'loss' && isAtLoss(cost))),
    );
  });

  protected productOf(cost: ProductCost): Product | null {
    return this.productsById().get(cost.productId) ?? null;
  }

  protected formatFactor(factor: number): string {
    return factor.toLocaleString('fr-FR', { maximumFractionDigits: 3 });
  }

  /** 8.3 → "8,3 %". */
  protected formatRate(rate: number): string {
    return `${rate.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %`;
  }

  /** A price, a unit or a cost may have moved: the figures are the server's to work out again. */
  protected onChanged(): void {
    this.resource.reload();
  }
}

/** Some unit of it sells for less than it cost. */
function isAtLoss(cost: ProductCost): boolean {
  return cost.units.some((unit) => unit.margin !== null && unit.margin < 0);
}
