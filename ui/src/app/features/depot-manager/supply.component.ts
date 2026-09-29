import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, of, switchMap } from 'rxjs';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { CostingApi } from '../../core/api/costing.api';
import { StockApi } from '../../core/api/stock.api';
import { Product, ProductCost, RoleApp, Supplier, SupplierPrice, Supply } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { HasRoleDirective } from '../../shared/directives/has-role.directive';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-supply',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, HasRoleDirective, AriaryPipe],
  template: `
    <div class="grid g2">
      <div class="card">
        <h2>
          Nouvel approvisionnement
          <small>entrée de marchandise au dépôt</small>
        </h2>
        <div class="form-row">
          <div class="fld" style="flex:1; min-width:200px;">
            <label for="supply-product">Produit</label>
            <select
              id="supply-product"
              class="field"
              [ngModel]="selectedProductId()"
              (ngModelChange)="pickProduct($event)"
            >
              @for (product of products(); track product.id) {
                <option [ngValue]="product.id">
                  {{ product.name }} — stock {{ product.stockQuantity }}
                </option>
              }
            </select>
          </div>
          <div class="fld" style="flex:1; min-width:200px;">
            <label for="supply-supplier">Fournisseur</label>
            <select
              id="supply-supplier"
              class="field"
              [ngModel]="selectedSupplierId()"
              (ngModelChange)="supplierId.set($event)"
            >
              @for (supplier of suppliers(); track supplier.id) {
                <option [ngValue]="supplier.id">{{ supplier.name }}</option>
              }
            </select>
          </div>
          <div class="fld">
            <label for="supply-qty">Quantité reçue</label>
            <input
              id="supply-qty"
              type="number"
              min="1"
              style="width:110px;"
              [ngModel]="quantity()"
              (ngModelChange)="quantity.set($event)"
            />
          </div>
          <div class="fld">
            <label for="supply-cost">Prix d'achat unitaire</label>
            <input
              id="supply-cost"
              type="number"
              min="0"
              step="1"
              style="width:130px;"
              placeholder="à saisir"
              [ngModel]="unitCost()"
              (ngModelChange)="typedCost.set(asAmount($event))"
            />
          </div>
          <!-- Booking goods in belongs to the depot manager alone. -->
          <button
            *appHasRole="RoleApp.DEPOT_MANAGER"
            class="btn"
            type="button"
            [disabled]="!canSubmit()"
            (click)="register()"
          >
            {{ busy() ? 'Enregistrement...' : 'Ajouter au stock' }}
          </button>
        </div>

        @if (selectedCost(); as cost) {
          @if (cost.lastUnitCost !== null) {
            <div class="muted cost-hint">
              Dernier prix payé : {{ cost.lastUnitCost | ariary }}
              @if (cost.lastSupplierName) {
                chez {{ cost.lastSupplierName }}
              }
              @if (cost.lastPurchaseDate) {
                le {{ cost.lastPurchaseDate | date: 'dd/MM/yyyy' }}
              }
            </div>
          }
        }
        @if (costWarning(); as warning) {
          <div class="cost-warning">{{ warning }}</div>
        }

        @if (selectedProduct(); as product) {
          <div class="grid g3" style="margin-top:4px;">
            <div class="kpi">
              <div class="label">Stock après réception</div>
              <div class="value">{{ product.stockQuantity + quantity() }}</div>
              <div class="hint">actuellement {{ product.stockQuantity }}</div>
            </div>
            <div class="kpi">
              <div class="label">Coût de la livraison</div>
              <div class="value">{{ deliveryCost() === null ? '—' : (deliveryCost() | ariary) }}</div>
              <div class="hint">
                coût moyen actuel
                {{ selectedCost()?.averageCost == null ? 'inconnu' : (selectedCost()?.averageCost | ariary) }}
              </div>
            </div>
            <div class="kpi">
              <div class="label">Marge unitaire à ce prix</div>
              <div class="value">{{ unitMargin() === null ? '—' : (unitMargin() | ariary) }}</div>
              <div class="hint">
                vendu {{ product.price | ariary }}
                @if (unitMarginRate() !== null) {
                  · {{ unitMarginRate() | number: '1.1-1' }} %
                }
              </div>
            </div>
          </div>
        }
      </div>

      <div class="card">
        <h2>
          Prix par fournisseur
          <small>{{ selectedProduct()?.name ?? '' }} — du moins cher au plus cher, en moyenne</small>
        </h2>
        @if (supplierPrices().length) {
          <table>
            <thead>
              <tr>
                <th>Fournisseur</th>
                <th class="num">Coût moyen</th>
                <th class="num">Dernier prix</th>
                <th class="num">Min – max</th>
                <th class="num">Unités</th>
                <th>Dernière livraison</th>
              </tr>
            </thead>
            <tbody>
              @for (price of supplierPrices(); track price.supplierId; let first = $first) {
                <tr>
                  <td>
                    {{ price.supplierName }}
                    @if (first && supplierPrices().length > 1) {
                      <span class="badge green">le moins cher</span>
                    }
                  </td>
                  <td class="num">{{ price.averageCost | ariary }}</td>
                  <td class="num">{{ price.lastUnitCost | ariary }}</td>
                  <td class="num muted">
                    {{ price.lowestCost | ariary }} – {{ price.highestCost | ariary }}
                  </td>
                  <td class="num">{{ price.units }}</td>
                  <td class="muted">{{ price.lastPurchaseDate | date: 'dd/MM/yyyy' }}</td>
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <div class="empty">Aucun prix d'achat enregistré pour ce produit.</div>
        }
      </div>
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Derniers approvisionnements</h2>
      @if (supplies().length) {
        <table>
          <thead>
            <tr>
              <th>Produit</th>
              <th class="num">Quantité</th>
              <th class="num">Prix d'achat</th>
              <th class="num">Total</th>
              <th>Fournisseur</th>
              <th>Date</th>
              <th>Par</th>
            </tr>
          </thead>
          <tbody>
            @for (supply of supplies(); track supply.id) {
              <tr>
                <td>{{ supply.product.name }}</td>
                <td class="num">+{{ supply.quantity }}</td>
                <td class="num">
                  {{ supply.unitCost === null ? '—' : (supply.unitCost | ariary) }}
                </td>
                <td class="num">
                  {{ supply.totalCost === null ? '—' : (supply.totalCost | ariary) }}
                </td>
                <td>
                  @if (supply.supplier; as supplier) {
                    {{ supplier.name }}
                  } @else {
                    <!-- An import entry. Named rather than left blank: the stock did rise,
                         and an empty cell would read as a supplier gone missing. -->
                    <span class="badge grey" title="Stock saisi par import de produits">
                      Import
                    </span>
                  }
                </td>
                <td class="muted">{{ supply.movementDate | date: 'dd/MM HH:mm' }}</td>
                <td>{{ supply.performedBy.fullName }}</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty">Aucun approvisionnement enregistré.</div>
      }
    </div>
  `,
  styles: `
    .cost-hint {
      font-size: 12px;
      margin-top: 6px;
    }

    .cost-warning {
      font-size: 12px;
      font-weight: 600;
      color: var(--red);
      margin-top: 6px;
    }
  `,
})
export class SupplyComponent {
  private readonly catalog = inject(CatalogApi);
  private readonly stock = inject(StockApi);
  private readonly costing = inject(CostingApi);
  private readonly toasts = inject(ToastService);

  protected readonly RoleApp = RoleApp;
  protected readonly productId = signal<number | null>(null);
  protected readonly supplierId = signal<number | null>(null);
  protected readonly quantity = signal(10);
  /**
   * What the operator typed in the cost field. `undefined` means "not touched", and the field
   * then shows the last price paid for the product; `null` means they cleared it on purpose.
   */
  protected readonly typedCost = signal<number | null | undefined>(undefined);
  protected readonly busy = signal(false);

  private readonly productResource = apiResource<Product[]>([], () => this.catalog.products());
  private readonly supplierResource = apiResource<Supplier[]>([], () => this.catalog.suppliers());
  private readonly supplyResource = apiResource<Supply[]>([], () => this.stock.supplies());
  private readonly costResource = apiResource<ProductCost[]>([], () =>
    this.costing.productCosts(),
  );

  protected readonly products = this.productResource.value;
  protected readonly suppliers = this.supplierResource.value;
  protected readonly supplies = this.supplyResource.value;

  /**
   * Falls back to the first entry until the user picks one, so the form is usable as
   * soon as the lists land. Derived rather than assigned in an effect: writing to a
   * signal from an effect is refused by Angular (NG0600), and a computed says the same
   * thing without the exception.
   */
  protected readonly selectedProductId = computed(
    () => this.productId() ?? this.products()[0]?.id ?? null,
  );
  protected readonly selectedSupplierId = computed(
    () => this.supplierId() ?? this.suppliers()[0]?.id ?? null,
  );

  protected readonly selectedProduct = computed(() =>
    this.products().find((p) => p.id === this.selectedProductId()),
  );
  protected readonly selectedCost = computed(() =>
    this.costResource.value().find((c) => c.productId === this.selectedProductId()),
  );

  /** The cost the form will send: what was typed, else the last price paid. */
  protected readonly unitCost = computed<number | null>(() => {
    const typed = this.typedCost();
    return typed !== undefined ? typed : (this.selectedCost()?.lastUnitCost ?? null);
  });

  protected readonly deliveryCost = computed(() => {
    const cost = this.unitCost();
    return cost === null ? null : cost * Number(this.quantity() || 0);
  });

  /** Sale price minus the cost typed — a subtraction, not the average the server keeps. */
  protected readonly unitMargin = computed(() => {
    const cost = this.unitCost();
    const product = this.selectedProduct();
    return cost === null || !product ? null : product.price - cost;
  });

  protected readonly unitMarginRate = computed(() => {
    const margin = this.unitMargin();
    const price = this.selectedProduct()?.price ?? 0;
    return margin === null || price === 0 ? null : (margin * 100) / price;
  });

  protected readonly costWarning = computed<string | null>(() => {
    const cost = this.unitCost();
    const product = this.selectedProduct();
    if (!product) {
      return null;
    }
    if (cost === null) {
      return "Sans prix d'achat, cette livraison ne pourra pas entrer dans le calcul des marges.";
    }
    if (cost >= product.price) {
      return `Prix d'achat supérieur ou égal au prix de vente (${product.price.toLocaleString('fr-FR')} Ar) : ce produit serait vendu à perte.`;
    }
    return null;
  });

  /** Bumped after a receipt, so the comparison is refetched for a product that did not change. */
  private readonly pricesVersion = signal(0);

  /** Refetched whenever the product changes; an error just leaves the table empty. */
  protected readonly supplierPrices = toSignal(
    toObservable(
      computed(() => ({ productId: this.selectedProductId(), version: this.pricesVersion() })),
    ).pipe(
      switchMap(({ productId }) =>
        productId === null
          ? of<SupplierPrice[]>([])
          : this.costing
              .supplierPrices(productId)
              .pipe(catchError(() => of<SupplierPrice[]>([]))),
      ),
    ),
    { initialValue: [] as SupplierPrice[] },
  );

  protected pickProduct(id: number): void {
    this.productId.set(id);
    // The cost typed was for the previous product; the new one starts from its own last price.
    this.typedCost.set(undefined);
  }

  protected canSubmit(): boolean {
    const cost = this.unitCost();
    return (
      !this.busy() &&
      this.selectedProductId() !== null &&
      this.selectedSupplierId() !== null &&
      Number(this.quantity()) > 0 &&
      (cost === null || cost >= 0)
    );
  }

  /** An empty cost field is "not given", never zero: a free delivery is not the same claim. */
  protected asAmount(value: unknown): number | null {
    if (value === null || value === undefined || value === '') {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  protected register(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.busy.set(true);
    this.stock
      .registerSupply({
        productId: this.selectedProductId()!,
        supplierId: this.selectedSupplierId()!,
        quantity: Number(this.quantity()),
        unitCost: this.unitCost(),
      })
      .subscribe({
        next: (supply) => {
          this.busy.set(false);
          this.typedCost.set(undefined);
          this.productResource.reload();
          this.supplyResource.reload();
          this.supplierResource.reload();
          this.costResource.reload();
          this.pricesVersion.update((version) => version + 1);
          this.toasts.show(
            `${supply.quantity} × ${supply.product.name} ajouté(s) au stock ` +
              `(${supply.supplier?.name ?? 'sans fournisseur'}).`,
          );
        },
        error: () => this.busy.set(false),
      });
  }
}
