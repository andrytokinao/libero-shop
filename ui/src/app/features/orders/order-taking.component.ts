import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { InvoiceApi } from '../../core/api/invoice.api';
import { PaymentMethod, PaymentStatus, Product, RoleApp } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/**
 * Taking an order on a phone: the table (or client), a tap per article, then check and send.
 *
 * <p>Built for someone who was never trained on it, with a customer waiting: no payment method
 * to choose — unpaid, or paid in cash when the shop lets order takers take money. The button sits
 * at the bottom of the screen, under the thumb, with the total next to it, and opens the recap
 * the order is read back from before it leaves: catching a wrong line there is what keeps
 * cancellations rare.
 */
@Component({
  selector: 'app-order-taking',
  standalone: true,
  imports: [FormsModule, AriaryPipe],
  template: `
    <div class="order-top">
      <div class="fld who">
        <label for="order-client">{{ auth.words().client }}</label>
        <input
          id="order-client"
          type="text"
          autocomplete="off"
          [placeholder]="auth.words().clientPlaceholder"
          [ngModel]="clientName()"
          (ngModelChange)="clientName.set($event)"
        />
      </div>
      <div class="fld what">
        <label for="order-search">Produit</label>
        <input
          id="order-search"
          type="search"
          autocomplete="off"
          placeholder="Rechercher…"
          [ngModel]="search()"
          (ngModelChange)="search.set($event)"
        />
      </div>
    </div>

    @if (categories().length > 1) {
      <div class="chips" role="tablist" aria-label="Catégories">
        <button
          type="button"
          class="chip"
          [class.active]="category() === null"
          (click)="category.set(null)"
        >
          Tout
        </button>
        @for (name of categories(); track name) {
          <button
            type="button"
            class="chip"
            [class.active]="category() === name"
            (click)="category.set(name)"
          >
            {{ name }}
          </button>
        }
      </div>
    }

    <div class="tiles">
      @for (product of visibleProducts(); track product.id) {
        <button
          type="button"
          class="tile"
          [class.picked]="quantityOf(product) > 0"
          [disabled]="remainingStock(product) <= 0"
          (click)="add(product)"
        >
          @if (quantityOf(product) > 0) {
            <span class="count" aria-label="Quantité dans la commande">{{ quantityOf(product) }}</span>
          }
          <span class="name">{{ product.name }}</span>
          <span class="price">{{ product.price | ariary }}</span>
          @if (remainingStock(product) <= 0) {
            <span class="out">Épuisé</span>
          }
        </button>
      } @empty {
        <div class="empty">Aucun produit ne correspond.</div>
      }
    </div>

    <!-- Under the thumb, always: what is in the order, and the one button that sends it. -->
    <div class="order-bar">
      @if (lines().length) {
        <button type="button" class="summary" (click)="reviewing.set(true)">
          <strong>{{ units() }} article(s)</strong>
          <span>{{ total() | ariary }} · voir</span>
        </button>
        <button type="button" class="btn send" [disabled]="sending()" (click)="reviewing.set(true)">
          Valider ›
        </button>
      } @else {
        <span class="muted hint">Touchez un produit pour l'ajouter à la commande.</span>
      }
    </div>

    @if (reviewing()) {
      <div class="modal-backdrop" (click)="onBackdrop($event)">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="order-review-title">
          <div class="modal-head">
            <div>
              <h2 id="order-review-title">Vérifiez la commande</h2>
              <div class="sub muted">{{ clientName() || auth.words().client + ' non précisé(e)' }}</div>
            </div>
            <button class="x" type="button" aria-label="Fermer" (click)="reviewing.set(false)">✕</button>
          </div>
          <div class="modal-body">
            <ul class="review">
              @for (line of lines(); track line.product.id) {
                <li>
                  <span class="name">{{ line.product.name }}</span>
                  <span class="qty">
                    <button class="qtybtn" type="button" aria-label="Retirer un" (click)="change(line.product, -1)">−</button>
                    {{ line.quantity }}
                    <button
                      class="qtybtn"
                      type="button"
                      aria-label="Ajouter un"
                      [disabled]="remainingStock(line.product) <= 0"
                      (click)="change(line.product, 1)"
                    >
                      +
                    </button>
                  </span>
                  <span class="sub-total">{{ line.product.price * line.quantity | ariary }}</span>
                </li>
              } @empty {
                <li class="muted">La commande est vide.</li>
              }
            </ul>
            <div class="total-row">
              <span>Total</span>
              <span>{{ total() | ariary }}</span>
            </div>
          </div>
          <div class="modal-foot send-actions">
            <button class="btn ghost" type="button" (click)="reviewing.set(false)">Modifier</button>
            @if (canCollect()) {
              <!-- The customer pays now: the cash is this person's to bring to the till. -->
              <button
                class="btn ghost"
                type="button"
                [disabled]="!lines().length || sending()"
                (click)="send(true)"
              >
                Payée en espèces — envoyer
              </button>
            }
            <button
              class="btn"
              type="button"
              [disabled]="!lines().length || sending()"
              (click)="send(false)"
            >
              {{ sending() ? 'Envoi…' : canCollect() ? 'Non payée — envoyer' : 'Confirmer et envoyer' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  // The tiles, chips and bottom bar are in styles.scss: the customer's QR-code page uses them too.
  styles: `
    :host {
      display: block;
    }
  `,
})
export class OrderTakingComponent {
  private readonly catalog = inject(CatalogApi);
  private readonly invoices = inject(InvoiceApi);
  private readonly toasts = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly search = signal('');
  protected readonly category = signal<string | null>(null);
  protected readonly clientName = signal('');
  protected readonly reviewing = signal(false);
  protected readonly sending = signal(false);

  /** Quantity per product id; resolved against the live catalogue. */
  private readonly quantities = signal<Record<number, number>>({});

  private readonly catalogResource = apiResource<Product[]>([], () => this.catalog.products());
  private readonly products = this.catalogResource.value;

  /** The rayons that have something to sell, as one-tap filters. */
  protected readonly categories = computed(() =>
    [...new Set(this.products().map((p) => p.category?.name).filter((n): n is string => !!n))].sort(
      (a, b) => a.localeCompare(b, 'fr'),
    ),
  );

  protected readonly visibleProducts = computed(() => {
    const term = this.search().trim().toLowerCase();
    const category = this.category();
    return this.products().filter(
      (p) =>
        (category === null || p.category?.name === category) &&
        (!term ||
          [p.name, p.category?.name ?? '', p.barcode ?? ''].some((field) =>
            field.toLowerCase().includes(term),
          )),
    );
  });

  protected readonly lines = computed(() =>
    this.products()
      .filter((p) => (this.quantities()[p.id] ?? 0) > 0)
      .map((product) => ({ product, quantity: this.quantities()[product.id] })),
  );

  protected readonly units = computed(() => this.lines().reduce((sum, l) => sum + l.quantity, 0));
  protected readonly total = computed(() =>
    this.lines().reduce((sum, l) => sum + l.product.price * l.quantity, 0),
  );

  protected quantityOf(product: Product): number {
    return this.quantities()[product.id] ?? 0;
  }

  protected remainingStock(product: Product): number {
    return product.stockQuantity - this.quantityOf(product);
  }

  protected add(product: Product): void {
    this.change(product, 1);
  }

  protected change(product: Product, delta: number): void {
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

  /** Taking the customer's money too: the shop allows it, or this account also holds a till. */
  protected readonly canCollect = computed(
    () => this.auth.settings().orderTakerCollects || this.auth.hasRole(RoleApp.CASHIER),
  );

  /** @param paid the customer paid in cash on the spot */
  protected send(paid: boolean): void {
    const lines = this.lines();
    if (!lines.length || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.invoices
      .createSale({
        clientName: this.clientName(),
        // Paid to an order taker, the server records it as their cash in hand, not as paid.
        paymentStatus: paid ? PaymentStatus.PAID : PaymentStatus.UNPAID,
        paymentMethod: PaymentMethod.CASH,
        lines: lines.map((l) => ({ productId: l.product.id, quantity: l.quantity })),
      })
      .subscribe({
        next: (invoice) => {
          this.sending.set(false);
          this.reviewing.set(false);
          this.quantities.set({});
          this.clientName.set('');
          this.search.set('');
          this.catalogResource.reload();
          this.toasts.show(
            invoice.paymentStatus === PaymentStatus.COLLECTED
              ? `Commande ${invoice.invoiceNumber} envoyée et encaissée — argent à remettre à la caisse.`
              : `Commande ${invoice.invoiceNumber} envoyée — ${invoice.clientName}.`,
          );
        },
        error: () => {
          this.sending.set(false);
          // The interceptor has said why; stock may have moved, so re-read it.
          this.catalogResource.reload();
        },
      });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.reviewing.set(false);
    }
  }
}
