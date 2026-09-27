import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { StockApi } from '../../core/api/stock.api';
import { Product, RoleApp, Supplier, Supply } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { HasRoleDirective } from '../../shared/directives/has-role.directive';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-supply',
  standalone: true,
  imports: [FormsModule, DatePipe, HasRoleDirective, AriaryPipe],
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
              (ngModelChange)="productId.set($event)"
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

        @if (selectedProduct(); as product) {
          <div class="grid g2" style="margin-top:4px;">
            <div class="kpi">
              <div class="label">Stock après réception</div>
              <div class="value">{{ product.stockQuantity + quantity() }}</div>
              <div class="hint">actuellement {{ product.stockQuantity }}</div>
            </div>
            <div class="kpi">
              <div class="label">Valeur ajoutée au stock</div>
              <div class="value">{{ product.price * quantity() | ariary }}</div>
              <div class="hint">{{ product.price | ariary }} l'unité</div>
            </div>
          </div>
        }
      </div>

      <div class="card">
        <h2>Derniers approvisionnements</h2>
        @if (supplies().length) {
          <table>
            <thead>
              <tr>
                <th>Produit</th>
                <th class="num">Quantité</th>
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
    </div>
  `,
})
export class SupplyComponent {
  private readonly catalog = inject(CatalogApi);
  private readonly stock = inject(StockApi);
  private readonly toasts = inject(ToastService);

  protected readonly RoleApp = RoleApp;
  protected readonly productId = signal<number | null>(null);
  protected readonly supplierId = signal<number | null>(null);
  protected readonly quantity = signal(10);
  protected readonly busy = signal(false);

  private readonly productResource = apiResource<Product[]>([], () => this.catalog.products());
  private readonly supplierResource = apiResource<Supplier[]>([], () => this.catalog.suppliers());
  private readonly supplyResource = apiResource<Supply[]>([], () => this.stock.supplies());

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

  protected canSubmit(): boolean {
    return (
      !this.busy() &&
      this.selectedProductId() !== null &&
      this.selectedSupplierId() !== null &&
      Number(this.quantity()) > 0
    );
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
      })
      .subscribe({
        next: (supply) => {
          this.busy.set(false);
          this.productResource.reload();
          this.supplyResource.reload();
          this.supplierResource.reload();
          this.toasts.show(
            `${supply.quantity} × ${supply.product.name} ajouté(s) au stock ` +
              `(${supply.supplier?.name ?? 'sans fournisseur'}).`,
          );
        },
        error: () => this.busy.set(false),
      });
  }
}
