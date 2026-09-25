import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { InvoiceApi } from '../../core/api/invoice.api';
import { PAYMENT_METHOD_LABELS, PaymentMethod, PaymentStatus, Product } from '../../core/models';
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
              [class.low]="product.lowStock"
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
              <option [ngValue]="PaymentStatus.PAID">Payée à la caisse</option>
              <option [ngValue]="PaymentStatus.UNPAID">Non payée (à régler au dépôt)</option>
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
                  <option [ngValue]="method">{{ methodLabels[method] }}</option>
                }
              </select>
            </div>
          }
        </div>

        <button
          class="btn block"
          type="button"
          style="padding:11px;"
          [disabled]="!cartItems().length || submitting()"
          (click)="validateSale()"
        >
          {{ submitting() ? 'Enregistrement...' : 'Valider la vente et générer la facture' }}
        </button>
      </div>
    </div>
  `,
})
export class NewSaleComponent {
  private readonly catalog = inject(CatalogApi);
  private readonly invoices = inject(InvoiceApi);
  private readonly toasts = inject(ToastService);

  protected readonly PaymentStatus = PaymentStatus;
  protected readonly paymentMethods = Object.values(PaymentMethod);
  protected readonly methodLabels = PAYMENT_METHOD_LABELS;

  protected readonly search = signal('');
  protected readonly clientName = signal('');
  protected readonly paymentStatus = signal<PaymentStatus>(PaymentStatus.PAID);
  protected readonly paymentMethod = signal<PaymentMethod>(PaymentMethod.CASH);
  protected readonly submitting = signal(false);

  /** Quantity per product id; the cart is resolved against the live catalogue. */
  private readonly quantities = signal<Record<number, number>>({});

  private readonly catalogResource = apiResource<Product[]>([], () => this.catalog.products());
  private readonly products = this.catalogResource.value;

  protected readonly visibleProducts = computed(() => {
    const term = this.search().trim().toLowerCase();
    if (!term) {
      return this.products();
    }
    return this.products().filter((p) =>
      [p.name, p.category?.name ?? '', p.barcode ?? ''].some((field) =>
        field.toLowerCase().includes(term),
      ),
    );
  });

  protected readonly cartItems = computed(() =>
    this.products()
      .filter((p) => (this.quantities()[p.id] ?? 0) > 0)
      .map((product) => ({ product, quantity: this.quantities()[product.id] })),
  );

  protected readonly total = computed(() =>
    this.cartItems().reduce((sum, item) => sum + item.product.price * item.quantity, 0),
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
    if (!cart.length || this.submitting()) {
      return;
    }
    this.submitting.set(true);

    this.invoices
      .createSale({
        clientName: this.clientName(),
        paymentStatus: this.paymentStatus(),
        paymentMethod: this.paymentMethod(),
        lines: cart.map((item) => ({ productId: item.product.id, quantity: item.quantity })),
      })
      .subscribe({
        next: (invoice) => {
          this.submitting.set(false);
          this.quantities.set({});
          this.clientName.set('');
          // The server has moved the stock; re-read it rather than guess the new figures.
          this.catalogResource.reload();
          this.toasts.show(
            `Vente enregistrée — facture ${invoice.invoiceNumber} ` +
              `(${invoice.sale.totalAmount.toLocaleString('fr-FR')} Ar)`,
          );
        },
        error: () => {
          this.submitting.set(false);
          // The interceptor has already shown why; refresh in case stock moved elsewhere.
          this.catalogResource.reload();
        },
      });
  }
}
