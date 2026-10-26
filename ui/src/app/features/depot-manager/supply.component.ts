import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, of, switchMap } from 'rxjs';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { CostingApi } from '../../core/api/costing.api';
import { StockApi } from '../../core/api/stock.api';
import { PackagingRequest, Product, ProductCost, RoleApp, Supplier, SupplierPrice, Supply } from '../../core/models';
import {
  SaleUnit,
  formatAmount,
  formatQuantity,
  parseQuantity,
  roundQuantity,
  stockLabel,
  unitChoiceLabel,
  unitsOf,
} from '../../core/sale/sale-unit';
import { StockUndo } from '../../core/stock/stock-undo';
import { ToastService } from '../../core/services/toast.service';
import { ProductStore } from '../../core/store/product.store';
import { ActionMenuComponent } from '../../shared/components/action-menu.component';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog.component';
import { ProductPickerComponent } from '../../shared/components/product-picker.component';
import { StockHistoryDialogComponent } from '../../shared/components/stock-history-dialog.component';
import { MovementAction, receiptHtml, supplyActions } from '../../core/stock/movement-actions';
import { printPage } from '../../core/utils/print.util';
import { SupplierCreateDialogComponent } from '../../shared/components/supplier-create-dialog.component';
import { LoadStateComponent } from '../../shared/components/load-state.component';
import { HasRoleDirective } from '../../shared/directives/has-role.directive';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/** The "+ unité" form, as typed. Strings because an empty field is not zero. */
interface NewUnitDraft {
  label: string;
  factor: string;
  /** The unit the contenance is counted in, by name; null for the base unit. */
  reference: string | null;
  price: string;
}

const EMPTY_UNIT: NewUnitDraft = { label: '', factor: '', reference: null, price: '' };

/** The list's last entry: not a supplier id, the way to add one. */
const NEW_SUPPLIER = -1;

@Component({
  selector: 'app-supply',
  standalone: true,
  imports: [
    LoadStateComponent,
    FormsModule,
    DatePipe,
    DecimalPipe,
    HasRoleDirective,
    AriaryPipe,
    ProductPickerComponent,
    SupplierCreateDialogComponent,
    ActionMenuComponent,
    ConfirmDialogComponent,
    StockHistoryDialogComponent,
  ],
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
            <app-product-picker
              inputId="supply-product"
              [products]="products()"
              [selected]="selectedProduct()"
              (picked)="pickProduct($event.product.id, $event.packagingId)"
              (unknownCode)="sayUnknown($event)"
            />
          </div>
          <div class="fld" style="flex:1; min-width:200px;">
            <label for="supply-supplier">Fournisseur</label>
            <select
              id="supply-supplier"
              class="field"
              [ngModel]="selectedSupplierId()"
              (ngModelChange)="pickSupplier($event, supplierSelect)"
              #supplierSelect
            >
              <!-- Goods bought at the market, or a shop that keeps no supplier list. -->
              <option [ngValue]="null">Sans fournisseur</option>
              @for (supplier of suppliers(); track supplier.id) {
                <option [ngValue]="supplier.id">{{ supplier.name }}</option>
              }
              <option [ngValue]="NEW_SUPPLIER">＋ Nouveau fournisseur…</option>
            </select>
          </div>
          <div class="fld">
            <label for="supply-unit">Reçu en</label>
            <!-- The unit the supplier delivers in: ten sacks are typed as 10, not as 1 750. -->
            <div class="unit-pick">
              <select
                id="supply-unit"
                class="field"
                [ngModel]="selectedUnit()?.packagingId ?? null"
                (ngModelChange)="pickUnit($event)"
              >
                @for (unit of units(); track unit.packagingId) {
                  <option [ngValue]="unit.packagingId">
                    {{ unitChoiceLabel(unit) }}
                    @if (unit.packagingId !== null) {
                      (× {{ formatAmount(unit.factor, selectedProduct()?.unit ?? null) }})
                    }
                  </option>
                }
              </select>
              <button
                *appHasRole="RoleApp.DEPOT_MANAGER"
                class="btn small ghost"
                type="button"
                title="Le fournisseur livre dans une unité que le produit n'a pas encore"
                [disabled]="!selectedProduct()"
                (click)="creatingUnit.set(!creatingUnit())"
              >
                ＋ unité
              </button>
            </div>
          </div>
          <div class="fld">
            <label for="supply-qty">Quantité reçue</label>
            <input
              id="supply-qty"
              type="number"
              min="0"
              step="any"
              style="width:110px;"
              [ngModel]="quantity()"
              (ngModelChange)="quantity.set($event)"
            />
          </div>
          <div class="fld">
            <label for="supply-cost">Prix d'achat par {{ selectedUnit()?.label ?? 'unité' }}</label>
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

        @if (creatingUnit() && selectedProduct(); as product) {
          <!-- Only what a receipt needs: the rest of the unit is edited from the stock screen. -->
          <form class="new-unit" (ngSubmit)="createUnit(product.id)">
            <div class="fld">
              <label for="new-unit-label">Nouvelle unité</label>
              <input
                id="new-unit-label"
                name="label"
                type="text"
                maxlength="16"
                autocomplete="off"
                placeholder="sac 25 kg"
                [ngModel]="newUnit().label"
                (ngModelChange)="patchNewUnit({ label: $event })"
              />
            </div>
            <div class="fld">
              <label for="new-unit-factor">Contient</label>
              <div class="suffixed">
                <input
                  id="new-unit-factor"
                  name="factor"
                  type="text"
                  inputmode="decimal"
                  autocomplete="off"
                  placeholder="25"
                  [ngModel]="newUnit().factor"
                  (ngModelChange)="patchNewUnit({ factor: $event })"
                />
                <!-- As printed on it: a 25 kg sack contains 25 kg. -->
                <select
                  name="reference"
                  class="field"
                  aria-label="Unité de référence"
                  [ngModel]="newUnit().reference"
                  (ngModelChange)="patchNewUnit({ reference: $event })"
                >
                  <option [ngValue]="null">{{ baseName() }}</option>
                  @for (p of selectedProduct()?.packagings ?? []; track p.id) {
                    <option [ngValue]="p.label">{{ p.label }}</option>
                  }
                </select>
              </div>
            </div>
            <div class="fld">
              <label for="new-unit-price">Prix de vente (Ar)</label>
              <input
                id="new-unit-price"
                name="price"
                type="text"
                inputmode="decimal"
                autocomplete="off"
                [ngModel]="newUnit().price"
                (ngModelChange)="patchNewUnit({ price: $event })"
              />
            </div>
            <button class="btn small" type="submit" [disabled]="busy() || !newUnitRequest()">Créer</button>
            <button class="btn small ghost" type="button" (click)="creatingUnit.set(false)">Annuler</button>
          </form>
        }

        @if (selectedCost(); as cost) {
          @if (cost.lastUnitCost !== null) {
            <div class="muted cost-hint">
              Dernier prix payé : {{ cost.lastUnitCost | ariary }} / {{ baseName() }}
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
              <div class="value">{{ formatAmount(stockAfter(), product.unit) }}</div>
              <div class="hint">
                actuellement {{ formatAmount(product.stockQuantity, product.unit) }}
                @if (receivedBase() !== quantityNumber()) {
                  · + {{ formatAmount(receivedBase(), product.unit) }}
                }
              </div>
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
              <div class="label">Marge par {{ selectedUnit()?.label ?? 'unité' }} à ce prix</div>
              <div class="value">{{ unitMargin() === null ? '—' : (unitMargin() | ariary) }}</div>
              <div class="hint">
                vendu {{ selectedUnit()?.price ?? product.price | ariary }}
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
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Fournisseur</th>
                  <th class="num">Coût moyen / {{ baseName() }}</th>
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
                    <td class="num">{{ formatQuantity(price.units) }}</td>
                    <td class="muted">{{ price.lastPurchaseDate | date: 'dd/MM/yyyy' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <div class="empty">Aucun prix d'achat enregistré pour ce produit.</div>
        }
      </div>
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Derniers approvisionnements</h2>
      <app-load-state [of]="supplyResource" what="les approvisionnements" />
      @if (supplies().length) {
        <div class="table-scroll">
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
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (supply of supplies(); track supply.id) {
                <tr [class.sp-cancelled]="supply.cancelledAt">
                  <td>
                    {{ supply.product.name }}
                    @if (supply.cancelledAt) {
                      <span class="badge grey" [title]="'Annulée le ' + (supply.cancelledAt | date: 'dd/MM HH:mm')">Annulée</span>
                    }
                  </td>
                  <td class="num">
                    +{{ formatAmount(supply.receivedQuantity, supply.unitLabel) }}
                    @if (supply.unitLabel) {
                      <div class="muted small-note">{{ formatAmount(supply.quantity, supply.product.unit) }}</div>
                    }
                  </td>
                  <td class="num">
                    {{ supply.unitCost === null ? '—' : (supply.unitCost | ariary) }}
                    @if (supply.unitCost !== null && supply.unitLabel) {
                      <span class="muted"> / {{ supply.unitLabel }}</span>
                    }
                  </td>
                  <td class="num">
                    {{ supply.totalCost === null ? '—' : (supply.totalCost | ariary) }}
                  </td>
                  <td>
                    @if (supply.supplier; as supplier) {
                      {{ supplier.name }}
                    } @else {
                      <!-- No supplier named. Said rather than left blank: the stock did rise,
                           and an empty cell would read as a supplier gone missing. -->
                      <span
                        class="badge grey"
                        title="Stock entré sans fournisseur : import, stock de départ d'un produit ou entrée saisie à la main"
                      >
                        Sans fournisseur
                      </span>
                    }
                  </td>
                  <td class="muted">{{ supply.movementDate | date: 'dd/MM HH:mm' }}</td>
                  <td>{{ supply.performedBy.fullName }}</td>
                  <td class="num">
                    <app-action-menu
                      [actions]="supplyActions(supply, true)"
                      [label]="'Actions sur la réception de ' + supply.product.name"
                      (picked)="onSupplyAction(supply, $event)"
                    />
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (supplyResource.loaded()) {
        <div class="empty">Aucun approvisionnement enregistré.</div>
      }
    </div>

    @if (historyOf(); as product) {
      <app-stock-history-dialog [product]="product" (closed)="historyOf.set(null)" />
    }

    @if (cancelling(); as supply) {
      <app-confirm-dialog
        title="Annuler cette réception ?"
        [message]="'Le stock de « ' + supply.product.name + ' » baisse de ' + formatAmount(supply.receivedQuantity, supply.unitLabel ?? supply.product.unit) + '. La réception reste dans l’historique, marquée annulée, avec la correction qui l’annule.'"
        confirmLabel="Annuler la réception"
        [danger]="true"
        [busy]="busy()"
        (confirmed)="cancelSupply(supply)"
        (closed)="cancelling.set(null)"
      />
    }

    @if (creatingSupplier()) {
      <app-supplier-create-dialog
        (created)="onSupplierCreated($event)"
        (closed)="creatingSupplier.set(false)"
      />
    }
  `,
  styles: `
    .sp-cancelled td {
      color: var(--ink-soft);
      text-decoration: line-through;
    }

    .sp-cancelled td:first-child,
    .sp-cancelled td:last-child {
      text-decoration: none;
    }

    .cost-hint {
      font-size: 12px;
      margin-top: 6px;
    }

    .unit-pick {
      display: flex;
      gap: 6px;
      align-items: center;
    }

    .new-unit {
      display: flex;
      flex-wrap: wrap;
      align-items: end;
      gap: 10px;
      margin-top: 10px;
      padding: 10px 12px;
      border: 1px dashed var(--line);
      border-radius: 9px;

      .fld {
        margin-bottom: 0;
        width: 140px;
      }
    }

    .suffixed {
      display: flex;
      align-items: center;
      gap: 6px;

      input {
        min-width: 0;
        flex: 1;
      }

      select {
        width: auto;
        max-width: 110px;
      }
    }

    .small-note {
      font-size: 11px;
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
  protected readonly unitChoiceLabel = unitChoiceLabel;
  private readonly stock = inject(StockApi);
  private readonly costing = inject(CostingApi);
  private readonly toasts = inject(ToastService);
  private readonly undo = inject(StockUndo);

  constructor() {
    // Taken back from the message: the lists show the movement cancelled.
    this.undo.undone$.pipe(takeUntilDestroyed()).subscribe(() => {
      this.supplyResource.reload();
      this.productResource.reload();
      this.costResource.reload();
    });
  }

  protected readonly RoleApp = RoleApp;
  protected readonly NEW_SUPPLIER = NEW_SUPPLIER;
  protected readonly creatingSupplier = signal(false);
  protected readonly productId = signal<number | null>(null);
  /** Undefined until one is picked — the first of the list then; null once "Sans fournisseur" is. */
  protected readonly supplierId = signal<number | null | undefined>(undefined);
  protected readonly quantity = signal<number | string>(10);
  /** The unit picked, by packaging id; null is the base unit. Reset with the product. */
  protected readonly unitId = signal<number | null>(null);
  protected readonly creatingUnit = signal(false);
  protected readonly newUnit = signal<NewUnitDraft>(EMPTY_UNIT);
  /**
   * What the operator typed in the cost field. `undefined` means "not touched", and the field
   * then shows the last price paid for the product; `null` means they cleared it on purpose.
   */
  protected readonly typedCost = signal<number | null | undefined>(undefined);
  protected readonly busy = signal(false);

  // The store keeps each "stock N" current, whoever sells or receives meanwhile.
  private readonly productResource = inject(ProductStore).list();
  private readonly supplierResource = apiResource<Supplier[]>([], () => this.catalog.suppliers());
  protected readonly supplyResource = apiResource<Supply[]>([], () => this.stock.supplies());
  private readonly costResource = apiResource<ProductCost[]>([], () =>
    this.costing.productCosts(),
  );

  protected readonly products = this.productResource.value;
  /** The suppliers still offered at a receipt. */
  protected readonly suppliers = computed(() => this.supplierResource.value().filter((s) => !s.retired));
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
  protected readonly selectedSupplierId = computed(() => {
    const picked = this.supplierId();
    return picked === undefined ? (this.suppliers()[0]?.id ?? null) : picked;
  });

  protected readonly selectedProduct = computed(() =>
    this.products().find((p) => p.id === this.selectedProductId()),
  );
  protected readonly selectedCost = computed(() =>
    this.costResource.value().find((c) => c.productId === this.selectedProductId()),
  );

  protected readonly units = computed(() => {
    const product = this.selectedProduct();
    return product ? unitsOf(product) : [];
  });
  /** The unit picked, or the base unit once the picked one is gone. */
  protected readonly selectedUnit = computed<SaleUnit | null>(
    () => this.units().find((unit) => unit.packagingId === this.unitId()) ?? this.units()[0] ?? null,
  );
  protected readonly baseName = computed(() => this.selectedProduct()?.unit ?? 'unité');

  protected readonly quantityNumber = computed(() => Number(this.quantity()) || 0);
  /** What the stock rises by, in base units: 10 sacks of 175 kapoka is 1 750. */
  protected readonly receivedBase = computed(() =>
    roundQuantity(this.quantityNumber() * (this.selectedUnit()?.factor ?? 1)),
  );
  protected readonly stockAfter = computed(() =>
    roundQuantity((this.selectedProduct()?.stockQuantity ?? 0) + this.receivedBase()),
  );

  /**
   * The cost the form will send, per unit received: what was typed, else the last price paid —
   * scaled by the server to the unit picked (the last kapoka price × 175 for a sack).
   */
  protected readonly unitCost = computed<number | null>(() => {
    const typed = this.typedCost();
    if (typed !== undefined) {
      return typed;
    }
    // Scaled by the server from the exact price: a tonne comes back to the ariary.
    const unitId = this.selectedUnit()?.packagingId ?? null;
    const last = this.selectedCost()?.units.find((unit) => unit.packagingId === unitId)?.lastCost;
    return last == null ? null : Math.round(last);
  });

  protected readonly deliveryCost = computed(() => {
    const cost = this.unitCost();
    return cost === null ? null : cost * this.quantityNumber();
  });

  /** The unit's sale price minus the cost typed — a subtraction, not the average the server keeps. */
  protected readonly unitMargin = computed(() => {
    const cost = this.unitCost();
    const unit = this.selectedUnit();
    return cost === null || !unit ? null : unit.price - cost;
  });

  protected readonly unitMarginRate = computed(() => {
    const margin = this.unitMargin();
    const price = this.selectedUnit()?.price ?? 0;
    return margin === null || price === 0 ? null : (margin * 100) / price;
  });

  protected readonly costWarning = computed<string | null>(() => {
    const cost = this.unitCost();
    const unit = this.selectedUnit();
    if (!unit) {
      return null;
    }
    if (cost === null) {
      return "Sans prix d'achat, cette livraison ne pourra pas entrer dans le calcul des marges.";
    }
    if (cost >= unit.price) {
      return `Prix d'achat supérieur ou égal au prix de vente (${unit.price.toLocaleString('fr-FR')} Ar par ${unit.label ?? 'unité'}) : ce produit serait vendu à perte.`;
    }
    return null;
  });

  /** The new unit as the server takes it, or null while it cannot be sent. */
  protected readonly newUnitRequest = computed<PackagingRequest | null>(() => {
    const draft = this.newUnit();
    const factor = parseQuantity(draft.factor);
    const price = parseQuantity(draft.price);
    return draft.label.trim() && factor !== null && price !== null
      ? { label: draft.label.trim(), factor, price, barcode: null, reference: draft.reference }
      : null;
  });

  protected readonly stockLabel = stockLabel;
  protected readonly formatAmount = formatAmount;
  protected readonly formatQuantity = formatQuantity;

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

  /** A carton's code scanned picks the carton too: the delivery is counted the way it came. */
  protected pickProduct(id: number, packagingId: number | null = null): void {
    this.productId.set(id);
    // The cost and the unit were the previous product's; the new one starts from its own.
    this.typedCost.set(undefined);
    this.unitId.set(packagingId);
    this.creatingUnit.set(false);
  }

  /**
   * "+ Nouveau fournisseur…" is not a supplier: it opens the form, and the list shows the one
   * picked before until the new one exists.
   */
  protected pickSupplier(id: number | null, select: HTMLSelectElement): void {
    if (id !== NEW_SUPPLIER) {
      this.supplierId.set(id);
      return;
    }
    this.creatingSupplier.set(true);
    // The model has not moved, so the list would keep showing the entry: put it back by hand.
    const current = this.selectedSupplierId();
    select.selectedIndex = current === null ? 0 : 1 + this.suppliers().findIndex((s) => s.id === current);
  }

  protected onSupplierCreated(supplier: Supplier): void {
    this.supplierResource.reload();
    this.supplierId.set(supplier.id);
  }

  protected readonly supplyActions = supplyActions;
  /** The product whose history is open, from a receipt's menu. */
  protected readonly historyOf = signal<Product | null>(null);
  /** The receipt about to be cancelled, while the confirmation is on screen. */
  protected readonly cancelling = signal<Supply | null>(null);

  protected onSupplyAction(supply: Supply, action: MovementAction): void {
    if (action === 'history') {
      this.historyOf.set(supply.product);
    } else if (action === 'print') {
      printPage(receiptHtml(supply, 'Moussa Shop'));
    } else {
      this.cancelling.set(supply);
    }
  }

  /** Undone by an opposite correction: the stock goes back, the receipt stays, marked. */
  protected cancelSupply(supply: Supply): void {
    this.busy.set(true);
    this.stock.cancelSupply(supply.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.cancelling.set(null);
        this.supplyResource.reload();
        this.productResource.reload();
        this.toasts.show(`Réception de « ${supply.product.name} » annulée ; le stock est corrigé.`);
      },
      error: () => {
        this.busy.set(false);
        this.cancelling.set(null);
      },
    });
  }

  protected sayUnknown(code: string): void {
    this.toasts.show(`Aucun produit ne porte le code ${code}.`);
  }

  protected pickUnit(packagingId: number | null): void {
    this.unitId.set(packagingId);
    // A price typed for a kapoka is not a sack's: start again from the last price, scaled.
    this.typedCost.set(undefined);
  }

  protected patchNewUnit(change: Partial<NewUnitDraft>): void {
    this.newUnit.update((draft) => ({ ...draft, ...change }));
  }

  /**
   * Creates the unit the supplier delivers in, without leaving the receipt, and picks it. The
   * server judges it as it would from the units dialog — a refusal is shown by the interceptor
   * and leaves the form as typed.
   */
  protected createUnit(productId: number): void {
    const request = this.newUnitRequest();
    if (!request || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.catalog.addPackaging(productId, request).subscribe({
      next: (units) => {
        this.busy.set(false);
        const created = units.packagings.find((p) => p.label === request.label);
        this.creatingUnit.set(false);
        this.newUnit.set(EMPTY_UNIT);
        this.productResource.reload();
        this.unitId.set(created?.id ?? null);
        this.typedCost.set(undefined);
        this.toasts.show(`Unité « ${request.label} » ajoutée à ${units.productName}.`);
      },
      error: () => this.busy.set(false),
    });
  }

  protected canSubmit(): boolean {
    const cost = this.unitCost();
    return (
      !this.busy() &&
      this.selectedProductId() !== null &&
      this.quantityNumber() > 0 &&
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
        supplierId: this.selectedSupplierId(),
        packagingId: this.selectedUnit()?.packagingId ?? null,
        quantity: this.quantityNumber(),
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
          this.undo.received(
            `${formatAmount(supply.receivedQuantity, supply.unitLabel ?? supply.product.unit)} de ${supply.product.name} ajouté(s) au stock ` +
              `(${supply.supplier?.name ?? 'sans fournisseur'}).`,
            [supply],
          );
        },
        error: () => this.busy.set(false),
      });
  }
}
