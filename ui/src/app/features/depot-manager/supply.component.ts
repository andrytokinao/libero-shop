import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SessionService } from '../../core/services/session.service';
import { ShopStore } from '../../core/services/shop-store.service';
import { ToastService } from '../../core/services/toast.service';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-supply',
  standalone: true,
  imports: [FormsModule, DatePipe, AriaryPipe],
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
              [ngModel]="productId()"
              (ngModelChange)="productId.set($event)"
            >
              @for (product of store.products(); track product.id) {
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
              [ngModel]="supplierId()"
              (ngModelChange)="supplierId.set($event)"
            >
              @for (supplier of store.suppliers(); track supplier.id) {
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
          <button class="btn" type="button" [disabled]="quantity() < 1" (click)="register()">
            Ajouter au stock
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
        @if (store.supplies().length) {
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
              @for (supply of store.supplies(); track supply.id) {
                <tr>
                  <td>{{ supply.product.name }}</td>
                  <td class="num">+{{ supply.quantity }}</td>
                  <td>{{ supply.supplier.name }}</td>
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
  protected readonly store = inject(ShopStore);
  private readonly session = inject(SessionService);
  private readonly toasts = inject(ToastService);

  protected readonly productId = signal(this.store.products()[0].id);
  protected readonly supplierId = signal(this.store.suppliers()[0].id);
  protected readonly quantity = signal(10);

  protected readonly selectedProduct = computed(() =>
    this.store.products().find((p) => p.id === this.productId()),
  );

  protected register(): void {
    const supply = this.store.registerSupply({
      productId: this.productId(),
      supplierId: this.supplierId(),
      quantity: Number(this.quantity()),
      performedBy: this.session.currentUser(),
    });

    if (supply) {
      this.toasts.show(
        `${supply.quantity} × ${supply.product.name} ajouté(s) au stock ` +
          `(${supply.supplier.name}).`,
      );
    }
  }
}
