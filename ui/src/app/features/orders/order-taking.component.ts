import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { InvoiceStore } from '../../core/store/invoice.store';
import {
  CatalogFolder,
  CategoryNode,
  PaymentMethod,
  PaymentStatus,
  Product,
  RoleApp,
} from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { Cart, lineTotal } from '../../core/sale/cart';
import { formatQuantity, stockLabel } from '../../core/sale/sale-unit';
import { followStock } from '../../core/sale/follow-stock';
import { ProductEntry } from '../../core/sale/product-entry';
import { ProductFinder } from '../../core/sale/product-finder';
import { SaleReference } from '../../core/sale/sale-reference';
import { stockShortagesOf } from '../../core/sale/stock-shortage';
import { ToastService } from '../../core/services/toast.service';
import { CameraScanButtonComponent } from '../../shared/components/camera-scan-button.component';
import { CartLineQuantityComponent } from '../../shared/components/cart-line-quantity.component';
import { UnitPicked, UnitPickerComponent } from '../../shared/components/unit-picker.component';
import { FolderTileComponent } from '../../shared/components/folder-tile.component';
import {
  CatalogNavigatorComponent,
  CatalogScope,
  FEATURED,
} from '../../shared/components/catalog-navigator.component';
import { FavoritesStore } from '../../core/store/favorites.store';
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
  imports: [
    FormsModule,
    AriaryPipe,
    CameraScanButtonComponent,
    CartLineQuantityComponent,
    UnitPickerComponent,
    CatalogNavigatorComponent,
    FolderTileComponent,
  ],
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

    <app-catalog-navigator [categories]="tree.value()" [scope]="scope()" (scopeChange)="scope.set($event)" />

    <div class="tiles">
      <!-- An explorer: the favourites and the rayons as folders first, then the products. -->
      @if (scope().kind === 'featured' && !entry.term() && favorites.any()) {
        <button type="button" class="tile tile-favorites" (click)="scope.set({ kind: 'favorites' })">
          <span class="name">★ Favoris</span>
        </button>
      }
      @for (folder of finder.folders(); track folder.rayon.id) {
        <app-folder-tile [folder]="folder" (opened)="openFolder($event)" />
      }
      @for (product of finder.products(); track product.id) {
        <div class="tile-cell">
        <div class="tile-box">
        <button
          type="button"
          class="tile"
          [class.picked]="cart.quantityOf(product) > 0"
          [class.short]="cart.isShort(product)"
          [disabled]="cart.remainingStock(product) <= 0"
          (click)="pick(product)"
        >
          @if (cart.quantityOf(product) > 0) {
            <span class="count" aria-label="Quantité dans la commande">{{ cart.countLabelOf(product) }}</span>
          }
          <span class="name">{{ product.name }}</span>
          <span class="price">
            {{ product.price | ariary }}@if (product.unit) {<span class="muted"> / {{ product.unit }}</span>}
          </span>
          @if (cart.remainingStock(product) <= 0) {
            <span class="out">Épuisé</span>
          }
        </button>
        <button
          type="button"
          class="tile-pin"
          [class.on]="favorites.isProductPinned(product.id)"
          [attr.aria-pressed]="favorites.isProductPinned(product.id)"
          [attr.aria-label]="(favorites.isProductPinned(product.id) ? 'Retirer des favoris : ' : 'Ajouter aux favoris : ') + product.name"
          (click)="favorites.toggleProduct(product.id)"
        >
          {{ favorites.isProductPinned(product.id) ? '★' : '☆' }}
        </button>
        </div>
        <!-- Sold in several units: one tile still, and the units open under it. -->
        @if (hasUnits(product) && (unitsOpen() === product.id || cart.quantityOf(product) > 0)) {
          <app-unit-picker [cart]="cart" [product]="product" (picked)="onUnitPicked(product, $event)" />
        }
        </div>
      } @empty {
        @if (!finder.pending() && !finder.folders().length) {
          <div class="empty">
            {{ scope().kind === 'favorites' ? 'Aucun favori : touchez ☆ sur un produit ou un rayon.' : 'Aucun produit ne correspond.' }}
          </div>
        }
      }
    </div>
    @if (finder.hasMore()) {
      <button
        type="button"
        class="btn ghost block"
        style="margin-top:10px;"
        [disabled]="finder.loadingMore()"
        (click)="finder.loadMore()"
      >
        {{ finder.loadingMore() ? 'Chargement…' : 'Afficher plus' }}
        ({{ finder.products().length }} sur {{ finder.total() }})
      </button>
    }

    <!-- Under the thumb, always: what is in the order, and the one button that sends it. -->
    <div class="order-bar">
      @if (!cart.isEmpty()) {
        <button type="button" class="summary" (click)="reviewing.set(true)">
          <strong>{{ formatQuantity(cart.units()) }} article(s)</strong>
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
              @for (line of cart.lines(); track line.product.id + ':' + line.unit.packagingId) {
                <li [class.short]="cart.isShort(line.product)">
                  <span class="name">
                    {{ line.product.name }}
                    @if (cart.isShort(line.product)) {
                      <span class="short-note">
                        — stock insuffisant : {{ stockLabel(line.product) }} disponible(s)
                      </span>
                    }
                  </span>
                  <span class="qty">
                    <app-cart-line-quantity [cart]="cart" [line]="line" />
                  </span>
                  <span class="sub-total">{{ lineTotal(line) | ariary }}</span>
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
  private readonly invoices = inject(InvoiceStore);
  private readonly toasts = inject(ToastService);
  protected readonly auth = inject(AuthService);

  /** Where the tiles are: best sellers, favourites, or inside a rayon (with the ones below it). */
  protected readonly scope = signal<CatalogScope>(FEATURED);
  protected readonly favorites = inject(FavoritesStore);
  protected readonly clientName = signal('');
  protected readonly reviewing = signal(false);
  protected readonly sending = signal(false);

  protected readonly cart = new Cart();
  private readonly reference = new SaleReference();
  /** The product whose unit buttons are open, before anything of it is in the order. */
  protected readonly unitsOpen = signal<number | null>(null);

  /** A product sold one way goes straight in; one sold in several units opens its unit buttons. */
  protected pick(product: Product): void {
    if (this.hasUnits(product)) {
      this.unitsOpen.update((open) => (open === product.id ? null : product.id));
    } else {
      this.cart.add(product);
    }
  }

  protected hasUnits(product: Product): boolean {
    return (product.packagings?.length ?? 0) > 0;
  }

  /** A folder tapped: the tiles go into it, and the words typed have done their job. */
  protected openFolder(folder: CatalogFolder): void {
    this.entry.clear();
    this.scope.set({ kind: 'rayon', id: folder.rayon.id });
  }

  protected onUnitPicked(product: Product, { unit, added }: UnitPicked): void {
    if (!added) {
      this.toasts.show(`${product.name} : pas assez de stock pour un ${unit.label ?? 'article'}.`);
    }
  }
  protected readonly stockLabel = stockLabel;
  protected readonly formatQuantity = formatQuantity;
  protected readonly lineTotal = lineTotal;


  /** The rayon tree, for the navigator's chips: a few dozen rows, read once. */
  protected readonly tree = apiResource<CategoryNode[]>([], () => this.catalog.categories());

  /** A scanner paired with the phone, or a code typed then "OK", adds the article directly. */
  protected readonly entry: ProductEntry = new ProductEntry(this.cart, () => this.finder.settled());

  protected readonly finder = new ProductFinder(
    computed(() => {
      const scope = this.scope();
      return {
        term: this.entry.term(),
        categoryId: scope.kind === 'rayon' ? scope.id : null,
        favorites: scope.kind === 'favorites',
        all: scope.kind === 'rayon' && !!scope.all,
      };
    }),
  );

  /** Taking the customer's money too: the shop allows it, or this account also holds a till. */
  protected readonly canCollect = computed(
    () => this.auth.settings().orderTakerCollects || this.auth.hasRole(RoleApp.CASHIER),
  );

  /** Not with a line in red: the server would only refuse it again. */
  protected readonly canSend = computed(
    () => !this.cart.isEmpty() && !this.cart.hasShortage() && !this.sending(),
  );

  constructor() {
    // Another phone or till selling the same article turns the line short here, before sending.
    followStock(this.cart);
  }

  /** @param paid the customer paid in cash on the spot */
  protected send(paid: boolean): void {
    if (!this.canSend()) {
      return;
    }
    this.sending.set(true);
    const draft = {
      clientName: this.clientName(),
      // Paid to an order taker, the server records it as their cash in hand, not as paid.
      paymentStatus: paid ? PaymentStatus.PAID : PaymentStatus.UNPAID,
      paymentMethod: PaymentMethod.CASH,
      lines: this.cart.toSaleLines(),
    };
    this.invoices
      // A phone loses the Wi-Fi more often than a till: sent again, the same order is one order.
      .createSale({ ...draft, clientReference: this.reference.for(draft) })
      .subscribe({
        next: (invoice) => {
          this.reference.renew();
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
