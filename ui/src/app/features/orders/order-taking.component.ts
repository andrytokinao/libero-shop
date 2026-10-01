import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { InvoiceApi } from '../../core/api/invoice.api';
import { CategoryNode, PaymentMethod, PaymentStatus, RoleApp } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { Cart } from '../../core/sale/cart';
import { ProductEntry } from '../../core/sale/product-entry';
import { ProductFinder } from '../../core/sale/product-finder';
import { stockShortagesOf } from '../../core/sale/stock-shortage';
import { ToastService } from '../../core/services/toast.service';
import { CameraScanButtonComponent } from '../../shared/components/camera-scan-button.component';
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
  imports: [FormsModule, AriaryPipe, CameraScanButtonComponent],
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
          enterkeyhint="go"
          placeholder="Nom ou code…"
          [ngModel]="entry.text()"
          (ngModelChange)="entry.text.set($event)"
          (keydown.enter)="entry.submit()"
        />
      </div>
      <app-camera-scan-button class="scan" (scanned)="entry.submit($event)" />
    </div>

    @if (rayons().length > 1) {
      <div class="chips" role="tablist" aria-label="Catégories">
        <button
          type="button"
          class="chip"
          [class.active]="category() === null"
          (click)="category.set(null)"
        >
          {{ finder.idle() ? 'Les plus vendus' : 'Tout' }}
        </button>
        @for (rayon of rayons(); track rayon.id) {
          <button
            type="button"
            class="chip"
            [class.active]="category() === rayon.id"
            (click)="category.set(rayon.id)"
          >
            {{ rayon.name }}
          </button>
        }
      </div>
    }

    <div class="tiles">
      @for (product of finder.products(); track product.id) {
        <button
          type="button"
          class="tile"
          [class.picked]="cart.quantityOf(product) > 0"
          [class.short]="cart.isShort(product)"
          [disabled]="cart.remainingStock(product) <= 0"
          (click)="cart.add(product)"
        >
          @if (cart.quantityOf(product) > 0) {
            <span class="count" aria-label="Quantité dans la commande">{{ cart.quantityOf(product) }}</span>
          }
          <span class="name">{{ product.name }}</span>
          <span class="price">{{ product.price | ariary }}</span>
          @if (cart.remainingStock(product) <= 0) {
            <span class="out">Épuisé</span>
          }
        </button>
      } @empty {
        @if (!finder.pending()) {
          <div class="empty">Aucun produit ne correspond.</div>
        }
      }
    </div>

    <!-- Under the thumb, always: what is in the order, and the one button that sends it. -->
    <div class="order-bar">
      @if (!cart.isEmpty()) {
        <button type="button" class="summary" (click)="reviewing.set(true)">
          <strong>{{ cart.units() }} article(s)</strong>
          <span>{{ cart.total() | ariary }} · voir</span>
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
              @for (line of cart.lines(); track line.product.id) {
                <li [class.short]="cart.isShort(line.product)">
                  <span class="name">
                    {{ line.product.name }}
                    @if (cart.isShort(line.product)) {
                      <span class="short-note">
                        — stock insuffisant : {{ line.product.stockQuantity }} disponible(s)
                      </span>
                    }
                  </span>
                  <span class="qty">
                    <button class="qtybtn" type="button" aria-label="Retirer un" (click)="cart.change(line.product, -1)">−</button>
                    {{ line.quantity }}
                    <button
                      class="qtybtn"
                      type="button"
                      aria-label="Ajouter un"
                      [disabled]="cart.remainingStock(line.product) <= 0"
                      (click)="cart.change(line.product, 1)"
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
              <span>{{ cart.total() | ariary }}</span>
            </div>
            @if (cart.hasShortage()) {
              <div class="short-note" role="alert">
                Le stock ne suffit pas pour les lignes en rouge : réduisez-les ou retirez-les.
              </div>
            }
          </div>
          <div class="modal-foot send-actions">
            <button class="btn ghost" type="button" (click)="reviewing.set(false)">Modifier</button>
            @if (canCollect()) {
              <!-- The customer pays now: the cash is this person's to bring to the till. -->
              <button
                class="btn ghost"
                type="button"
                [disabled]="!canSend()"
                (click)="send(true)"
              >
                Payée en espèces — envoyer
              </button>
            }
            <button
              class="btn"
              type="button"
              [disabled]="!canSend()"
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

  /** The rayon picked, by id: the server widens it to the rayons below. */
  protected readonly category = signal<number | null>(null);
  protected readonly clientName = signal('');
  protected readonly reviewing = signal(false);
  protected readonly sending = signal(false);

  protected readonly cart = new Cart();

  private readonly tree = apiResource<CategoryNode[]>([], () => this.catalog.categories());

  /** The top-level rayons as one-tap filters; picking one includes everything filed below it. */
  protected readonly rayons = computed(() => this.tree.value().filter((node) => node.depth === 0));

  /** A scanner paired with the phone, or a code typed then "OK", adds the article directly. */
  protected readonly entry: ProductEntry = new ProductEntry(this.cart, () => this.finder.settled());

  protected readonly finder = new ProductFinder(
    computed(() => ({ term: this.entry.term(), categoryId: this.category() })),
  );

  /** Taking the customer's money too: the shop allows it, or this account also holds a till. */
  protected readonly canCollect = computed(
    () => this.auth.settings().orderTakerCollects || this.auth.hasRole(RoleApp.CASHIER),
  );

  /** Not with a line in red: the server would only refuse it again. */
  protected readonly canSend = computed(
    () => !this.cart.isEmpty() && !this.cart.hasShortage() && !this.sending(),
  );

  /** @param paid the customer paid in cash on the spot */
  protected send(paid: boolean): void {
    if (!this.canSend()) {
      return;
    }
    this.sending.set(true);
    this.invoices
      .createSale({
        clientName: this.clientName(),
        // Paid to an order taker, the server records it as their cash in hand, not as paid.
        paymentStatus: paid ? PaymentStatus.PAID : PaymentStatus.UNPAID,
        paymentMethod: PaymentMethod.CASH,
        lines: this.cart.toSaleLines(),
      })
      .subscribe({
        next: (invoice) => {
          this.sending.set(false);
          this.reviewing.set(false);
          this.cart.clear();
          this.clientName.set('');
          this.entry.clear();
          this.finder.refresh();
          this.toasts.show(
            invoice.paymentStatus === PaymentStatus.COLLECTED
              ? `Commande ${invoice.invoiceNumber} envoyée et encaissée — argent à remettre à la caisse.`
              : `Commande ${invoice.invoiceNumber} envoyée — ${invoice.clientName}.`,
          );
        },
        error: (error: unknown) => {
          this.sending.set(false);
          // The recap stays open: the lines the shelf cannot fill turn red in it.
          this.cart.applyStock(stockShortagesOf(error));
          // The interceptor has said why; stock may have moved, so re-read it.
          this.finder.refresh();
        },
      });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.reviewing.set(false);
    }
  }
}
