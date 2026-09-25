import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  PAYMENT_METHOD_LABELS,
  PaymentMethod,
  PaymentStatus,
  Product,
  calculateTotal,
} from '../../core/models';
import { SessionService } from '../../core/services/session.service';
import { CartItem, LOW_STOCK_THRESHOLD, ShopStore } from '../../core/services/shop-store.service';
import { ToastService } from '../../core/services/toast.service';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-new-sale',
  standalone: true,
  imports: [FormsModule, AriaryPipe],
  template: `
    <div class="grid g2">
      <div class="card">
        <h2>Produits <small>cliquez pour ajouter au panier</small></h2>
        <div class="form-row">
          <div class="fld" style="flex:1; min-width:220px;">
            <label for="product-search">Recherche produit</label>
            <input
              id="product-search"
              type="text"
              placeholder="Nom, catégorie ou code-barres"
              [ngModel]="search()"
              (ngModelChange)="search.set($event)"
            />
          </div>
        </div>
        <div class="prod-grid">
          @for (product of visibleProducts(); track product.id) {
            <button
              type="button"
              class="prod-card"
              [class.low]="product.stockQuantity < lowStockThreshold"
              [disabled]="remainingStock(product) <= 0"
              (click)="addToCart(product)"
            >
              <div class="pname">{{ product.name }}</div>
              <div class="pcat">{{ product.category?.name ?? 'Sans catégorie' }}</div>
              <div class="pprice">{{ product.price | ariary }}</div>
              <div class="pstock">Disponible : {{ remainingStock(product) }}</div>
            </button>
          } @empty {
            <div class="empty">Aucun produit ne correspond à la recherche.</div>
          }
        </div>
      </div>

      <div class="card">
        <h2>Panier</h2>
        @if (cartItems().length) {
          <table>
            <thead>
              <tr>
                <th>Produit</th>
                <th>Quantité</th>
                <th class="num">Sous-total</th>
              </tr>
            </thead>
            <tbody>
              @for (item of cartItems(); track item.product.id) {
                <tr>
                  <td>{{ item.product.name }}</td>
                  <td>
                    <button class="qtybtn" type="button" (click)="changeQuantity(item.product, -1)">
                      −
                    </button>
                    {{ item.quantity }}
                    <button
                      class="qtybtn"
                      type="button"
                      [disabled]="item.quantity >= item.product.stockQuantity"
                      (click)="changeQuantity(item.product, 1)"
                    >
                      +
                    </button>
                  </td>
                  <td class="num">{{ item.product.price * item.quantity | ariary }}</td>
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <div class="empty">Panier vide — sélectionnez des produits.</div>
        }

        <div class="total-row">
          <span>Total</span>
          <span>{{ total() | ariary }}</span>
        </div>

        <div class="form-row" style="margin-top:16px;">
          <div class="fld" style="flex:1; min-width:180px;">
            <label for="client-name">Client</label>
            <input
              id="client-name"
              type="text"
              placeholder="Client comptoir"
              [ngModel]="clientName()"
              (ngModelChange)="clientName.set($event)"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="fld" style="flex:1; min-width:180px;">
            <label for="payment-status">Statut du paiement</label>
            <select
              id="payment-status"
              class="field"
              [ngModel]="paymentStatus()"
              (ngModelChange)="paymentStatus.set($event)"
            >
              <option [value]="PaymentStatus.PAID">Payée à la caisse</option>
              <option [value]="PaymentStatus.UNPAID">Non payée (à régler au dépôt)</option>
            </select>
          </div>
          @if (paymentStatus() === PaymentStatus.PAID) {
            <div class="fld" style="flex:1; min-width:160px;">
              <label for="payment-method">Mode de paiement</label>
              <select
                id="payment-method"
                class="field"
                [ngModel]="paymentMethod()"
                (ngModelChange)="paymentMethod.set($event)"
              >
                @for (method of paymentMethods; track method) {
                  <option [value]="method">{{ methodLabels[method] }}</option>
                }
              </select>
            </div>
          }
        </div>

        <button
          class="btn block"
          type="button"
          style="padding:11px;"
          [disabled]="!cartItems().length"
          (click)="validateSale()"
        >
          Valider la vente et générer la facture
        </button>
      </div>
    </div>
  `,
})
export class NewSaleComponent {
  private readonly store = inject(ShopStore);
  private readonly session = inject(SessionService);
  private readonly toasts = inject(ToastService);

  protected readonly PaymentStatus = PaymentStatus;
  protected readonly paymentMethods = Object.values(PaymentMethod);
  protected readonly methodLabels = PAYMENT_METHOD_LABELS;
  protected readonly lowStockThreshold = LOW_STOCK_THRESHOLD;

  protected readonly search = signal('');
  protected readonly clientName = signal('');
  protected readonly paymentStatus = signal<PaymentStatus>(PaymentStatus.PAID);
  protected readonly paymentMethod = signal<PaymentMethod>(PaymentMethod.CASH);

  /** Quantity per product id — the cart is resolved against live stock. */
  private readonly quantities = signal<Record<number, number>>({});

  protected readonly visibleProducts = computed(() => {
    const term = this.search().trim().toLowerCase();
    if (!term) {
      return this.store.products();
    }
    return this.store
      .products()
      .filter((p) =>
        [p.name, p.category?.name ?? '', p.barcode ?? ''].some((field) =>
          field.toLowerCase().includes(term),
        ),
      );
  });

  protected readonly cartItems = computed<CartItem[]>(() =>
    this.store
      .products()
      .filter((p) => (this.quantities()[p.id] ?? 0) > 0)
      .map((p) => ({ product: p, quantity: this.quantities()[p.id] })),
  );

  protected readonly total = computed(() =>
    calculateTotal(
      this.cartItems().map((item) => ({
        id: 0,
        quantity: item.quantity,
        unitPrice: item.product.price,
        product: item.product,
      })),
    ),
  );

  protected remainingStock(product: Product): number {
    return product.stockQuantity - (this.quantities()[product.id] ?? 0);
  }

  protected addToCart(product: Product): void {
    this.changeQuantity(product, 1);
  }

  protected changeQuantity(product: Product, delta: number): void {
    this.quantities.update((current) => {
      const next = (current[product.id] ?? 0) + delta;
      const updated = { ...current };
      if (next <= 0) {
        delete updated[product.id];
      } else {
        updated[product.id] = Math.min(next, product.stockQuantity);
      }
      return updated;
    });
  }

  protected validateSale(): void {
    const cart = this.cartItems();
    if (!cart.length) {
      return;
    }

    const invoice = this.store.registerSale({
      seller: this.session.currentUser(),
      clientName: this.clientName(),
      cart,
      paymentStatus: this.paymentStatus(),
      paymentMethod: this.paymentMethod(),
    });

    this.quantities.set({});
    this.clientName.set('');
    this.toasts.show(
      `Vente enregistrée — facture ${invoice.invoiceNumber} ` +
        `(${invoice.sale.totalAmount.toLocaleString('fr-FR')} Ar)`,
    );
  }
}
