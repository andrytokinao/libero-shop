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
  styles: `
    :host {
      display: block;
    }

    .order-top {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;

      .fld {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }

      .who {
        flex: 1 1 140px;
      }

      .what {
        flex: 2 1 200px;
      }

      label {
        font-size: 11.5px;
        font-weight: 600;
        color: var(--ink-soft);
      }

      input {
        font-size: 16px;
        padding: 11px 12px;
      }
    }

    .chips {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      padding: 12px 0 2px;
      -webkit-overflow-scrolling: touch;
      /* Swiped with a finger: the scrollbar would only draw a grey line under the chips. */
      scrollbar-width: none;

      &::-webkit-scrollbar {
        display: none;
      }
    }

    /* Up to three choices: one per line on a phone, the main one at the bottom under the thumb. */
    @media (max-width: 560px) {
      .send-actions {
        flex-direction: column;
        align-items: stretch;

        .btn {
          padding: 14px;
          font-size: 15px;
        }
      }
    }

    .chip {
      flex: 0 0 auto;
      border: 1px solid var(--line);
      background: #fff;
      border-radius: 20px;
      padding: 8px 14px;
      font: inherit;
      font-size: 14px;
      color: inherit;
      cursor: pointer;

      &.active {
        background: var(--brand);
        border-color: var(--brand);
        color: #fff;
      }
    }

    .tiles {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
      gap: 10px;
      margin-top: 12px;
    }

    .tile {
      position: relative;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      gap: 6px;
      min-height: 84px;
      padding: 12px;
      border: 2px solid var(--line);
      border-radius: 12px;
      background: #fff;
      text-align: left;
      font: inherit;
      color: inherit;
      cursor: pointer;

      &.picked {
        border-color: var(--brand);
        background: var(--brand-soft);
      }

      &:disabled {
        opacity: 0.45;
        cursor: not-allowed;
      }

      .name {
        font-weight: 600;
        font-size: 14.5px;
        line-height: 1.25;
        padding-right: 26px;
      }

      .price {
        font-weight: 700;
        color: var(--brand-dark);
      }

      .out {
        font-size: 12px;
        color: var(--red);
        font-weight: 600;
      }

      .count {
        position: absolute;
        top: 8px;
        right: 8px;
        min-width: 26px;
        height: 26px;
        padding: 0 6px;
        border-radius: 13px;
        background: var(--brand);
        color: #fff;
        font-weight: 700;
        font-size: 14px;
        display: flex;
        align-items: center;
        justify-content: center;
      }
    }

    /* Sticky rather than fixed: it stays under the thumb while the tiles scroll, and on a
       desktop it stays inside the page instead of sliding under the sidebar. */
    .order-bar {
      position: sticky;
      bottom: 0;
      z-index: 30;
      display: flex;
      align-items: center;
      gap: 10px;
      margin-top: 14px;
      padding: 10px 14px calc(10px + env(safe-area-inset-bottom));
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 12px 12px 0 0;
      box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.08);

      .summary {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        border: none;
        background: none;
        font: inherit;
        color: inherit;
        text-align: left;
        cursor: pointer;
        padding: 4px 0;

        span {
          color: var(--ink-soft);
          font-size: 13.5px;
        }
      }

      .send {
        flex: 0 0 auto;
        padding: 14px 26px;
        font-size: 16px;
      }

      .hint {
        padding: 10px 0;
      }
    }

    .review {
      list-style: none;
      margin: 0;
      padding: 0;

      li {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 10px 0;
        border-bottom: 1px solid var(--line);
      }

      .name {
        flex: 1;
        font-weight: 600;
      }

      .qty {
        display: flex;
        align-items: center;
        gap: 8px;
      }

      .sub-total {
        min-width: 80px;
        text-align: right;
      }
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
