import {
  Component,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InvoiceStore } from '../../core/store/invoice.store';
import { Invoice, Product } from '../../core/models';
import { Cart, CartLine, lineTotal } from '../../core/sale/cart';
import { baseUnitOf, formatQuantity, stockLabel } from '../../core/sale/sale-unit';
import { followStock } from '../../core/sale/follow-stock';
import { HeldSale, HeldSales } from '../../core/sale/held-sales';
import { ProductEntry } from '../../core/sale/product-entry';
import { ProductFinder } from '../../core/sale/product-finder';
import { ShortcutMap, isEnabled } from '../../core/sale/shortcuts';
import { stockShortagesOf } from '../../core/sale/stock-shortage';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { CameraScanButtonComponent } from '../../shared/components/camera-scan-button.component';
import { CartLineQuantityComponent } from '../../shared/components/cart-line-quantity.component';
import { IconComponent } from '../../shared/components/icon.component';
import { InvoiceDetailFlowComponent } from '../../shared/components/invoice-detail-flow.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';
import { SaleCheckout, SaleCheckoutDialogComponent } from './sale-checkout-dialog.component';

/**
 * Where the till goes to one column, products above the basket — the `.g2` breakpoint of
 * styles.scss, which the key hints and the total bar below follow too.
 */
const COMPACT_LAYOUT = '(max-width: 980px)';

/**
 * The till: scan or type, check the basket, take the money.
 *
 * <p>Made to be worked from the keyboard and a barcode scanner without touching the mouse — the
 * product field keeps the focus, Enter adds, the function keys of {@link keys} do the rest — and
 * to serve the next customer while one goes back for something they forgot ({@link HeldSales}).
 */
@Component({
  selector: 'app-new-sale',
  standalone: true,
  imports: [
    FormsModule,
    AriaryPipe,
    DatePipe,
    CameraScanButtonComponent,
    SaleCheckoutDialogComponent,
    InvoiceDetailFlowComponent,
    IconComponent,
    CartLineQuantityComponent,
  ],
  host: { '(document:keydown)': 'onKeydown($event)' },
  styles: `
    :host {
      display: block;
    }

    div.key-hint {
      display: flex;
    }

    /* The phone's bottom bar, its basket panel and their backdrop: nothing of them on a desk. */
    .sale-bar,
    .sheet-backdrop,
    .sheet-close {
      display: none;
    }

    /* ------------------------------------------------------------------------------------
       One column (COMPACT_LAYOUT): the products take the screen; the search shrinks to its
       field and stays in view; the basket hides behind the bottom bar's "Détails".
       ------------------------------------------------------------------------------------ */
    @media (max-width: 980px) {
      :host {
        --sticky-top: 0px;
      }

      /* Function keys mean nothing on a phone. The keys stay bound — a tablet may have a
         keyboard. */
      .key-hint {
        display: none !important;
      }

      .products-card {
        padding-top: 0;

        > h2,
        .grid-caption,
        label[for='product-search'] {
          display: none;
        }
      }

      .search-row {
        position: sticky;
        top: var(--sticky-top);
        z-index: 5;
        margin: 0 -14px 10px;
        padding: 10px 14px;
        gap: 8px;
        flex-wrap: nowrap;
        background: var(--panel);
        border-radius: 10px 10px 0 0;
        border-bottom: 1px solid var(--line);

        .fld {
          margin: 0;
          min-width: 0 !important;
        }
      }

      .cart-card {
        display: none;

        &.sheet-open {
          display: block;
          position: fixed;
          left: 0;
          right: 0;
          bottom: 0;
          z-index: 60;
          max-height: 82vh;
          overflow-y: auto;
          padding-bottom: calc(16px + env(safe-area-inset-bottom));
          border-radius: 16px 16px 0 0;
          box-shadow: 0 -12px 32px rgba(0, 0, 0, 0.22);
          animation: sheet-up 160ms ease-out;

          table {
            width: 100%;
          }
        }
      }

      .sheet-backdrop {
        display: block;
        position: fixed;
        inset: 0;
        z-index: 59;
        background: rgba(21, 36, 32, 0.45);
      }

      .sheet-close {
        display: inline-flex;
        vertical-align: middle;
        margin: -4px 6px 0 -4px;
        padding: 4px;
        border: none;
        border-radius: 50%;
        background: none;
        color: inherit;
        cursor: pointer;
      }

      .sale-bar {
        position: sticky;
        bottom: 0;
        z-index: 30;
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 12px -16px -16px;
        padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
        background: var(--panel);
        border-top: 1px solid var(--line);
        box-shadow: 0 -4px 14px rgba(0, 0, 0, 0.08);

        &.short .sale-bar-sum strong {
          color: var(--red);
        }

        .btn {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 11px 14px;
        }
      }

      .sale-bar-sum {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-width: 0;
        line-height: 1.2;

        strong {
          font-size: 18px;
        }

        span {
          font-size: 12px;
          color: var(--ink-soft);
        }
      }

      .held-count {
        min-width: 20px;
        height: 20px;
        padding: 0 6px;
        border-radius: 10px;
        background: var(--amber);
        color: var(--on-brand);
        font-size: 11.5px;
        line-height: 20px;
        text-align: center;
      }
    }

    /* The app's own bar is sticky above everything at this width: the search sits under it. */
    @media (max-width: 900px) {
      :host {
        --sticky-top: var(--mobile-bar-h);
      }
    }

    /* A slide only: a panel that fades in shows the products through it for a moment. */
    @keyframes sheet-up {
      from { transform: translateY(32px); }
      to { transform: none; }
    }

    @media (prefers-reduced-motion: reduce) {
      .cart-card.sheet-open { animation: none; }
    }
  `,
  template: `
    <div class="grid g2">
      <div class="card products-card">
        <h2>Produits <small>scannez, tapez un code puis Entrée, ou cliquez</small></h2>
        <!-- On a phone this row is all there is above the products, and it stays in view
             while they scroll: the next scan or search is always one tap away. -->
        <div class="form-row search-row" style="align-items:flex-end;">
          <div class="fld" style="flex:1; min-width:180px;">
            <label for="product-search">Produit</label>
            <input
              #productField
              id="product-search"
              type="text"
              autocomplete="off"
              placeholder="Code-barres, code ou nom — 3*code pour 3 unités"
              [ngModel]="entry.text()"
              (ngModelChange)="entry.text.set($event)"
              (keydown.enter)="entry.submit()"
            />
          </div>
          <app-camera-scan-button (scanned)="entry.submit($event)" />
        </div>
        <div class="muted key-hint" style="font-size:12px; margin:0 0 8px; flex-wrap:wrap; gap:4px 12px;">
          @for (shortcut of keys.shortcuts; track shortcut.key) {
            <span [style.opacity]="isEnabled(shortcut) ? 1 : 0.45">
              <kbd>{{ shortcut.key }}</kbd> {{ shortcut.label }}
            </span>
          }
        </div>
        <div class="muted grid-caption" style="font-size:12px; margin:0 0 8px;">
          {{ finder.idle() ? 'Les plus vendus ces 30 derniers jours' : 'Résultats de la recherche' }}
          @if (finder.pending()) {
            <span> — recherche…</span>
          }
        </div>
        <div class="prod-grid">
          @for (product of finder.products(); track product.id) {
            <!-- The count and the minus sit beside the card, not in it: a button cannot hold
                 another, and a tap on "−" must never also count as a tap on the product. -->
            <div class="prod-item">
              <button
                type="button"
                class="prod-card"
                [class.low]="product.lowStock"
                [class.picked]="cart.quantityOf(product) > 0"
                [class.short]="cart.isShort(product)"
                [disabled]="cart.remainingStock(product) <= 0"
                (click)="pick(product)"
              >
                <div class="pname">{{ product.name }}</div>
                <div class="pcat">{{ product.category?.name ?? 'Sans catégorie' }}</div>
                <div class="pprice">
                  {{ product.price | ariary }}@if (product.unit) {<span class="muted"> / {{ product.unit }}</span>}
                </div>
                <div class="pstock">Disponible : {{ stockLabel(product, cart.remainingStock(product)) }}</div>
              </button>
              @if (cart.quantityOf(product) > 0) {
                <span
                  class="prod-count"
                  [class.short]="cart.isShort(product)"
                  aria-label="Quantité dans le panier"
                >
                  {{ cart.countLabelOf(product) }}
                </span>
                <button
                  type="button"
                  class="prod-minus"
                  [attr.aria-label]="'Retirer un ' + product.name"
                  (click)="unpick(product)"
                >
                  −
                </button>
              }
            </div>
          } @empty {
            @if (!finder.pending()) {
              <div class="empty">
                {{ finder.idle() ? 'Aucun produit au catalogue.' : 'Aucun produit ne correspond à la recherche.' }}
              </div>
            }
          }
        </div>
      </div>

      <!-- The basket. Beside the products on a desktop; on a phone, a panel brought up from the
           bottom bar's "Détails" — the same markup either way, so both edit the same lines. -->
      @if (cartSheetOpen()) {
        <div class="sheet-backdrop" (click)="cartSheetOpen.set(false)"></div>
      }
      <div
        class="card cart-card"
        [class.sheet-open]="cartSheetOpen()"
        [attr.role]="cartSheetOpen() ? 'dialog' : null"
        [attr.aria-label]="cartSheetOpen() ? 'Panier' : null"
      >
        <h2>
          <button
            class="sheet-close"
            type="button"
            aria-label="Fermer le panier"
            (click)="cartSheetOpen.set(false)"
          >
            <app-icon name="close" [size]="20" />
          </button>
          Panier
          <button
            class="btn ghost"
            type="button"
            style="float:right; padding:4px 10px; font-size:12px;"
            [disabled]="!canHold()"
            (click)="holdSale()"
          >
            Mettre en attente <span class="key-hint">(F4)</span>
          </button>
        </h2>
        @if (held.count()) {
          <div class="chips" aria-label="Ventes en attente" style="margin-bottom:10px;">
            @for (sale of held.list(); track sale.id) {
              <button
                type="button"
                class="chip"
                [disabled]="!canResume()"
                [title]="'En attente depuis ' + (sale.heldAt | date: 'HH:mm') + ' — cliquer pour reprendre'"
                (click)="resume(sale.id)"
              >
                ⏸ {{ sale.clientName || auth.words().client }} · {{ formatQuantity(unitsOf(sale)) }} art. · {{ totalOf(sale) | ariary }}
              </button>
            }
          </div>
        }
        @if (!cart.isEmpty()) {
          <table>
            <thead>
              <tr>
                <th>Produit</th>
                <th>Quantité</th>
                <th class="num">Sous-total</th>
              </tr>
            </thead>
            <tbody>
              @for (item of cart.lines(); track item.product.id + ':' + item.unit.packagingId) {
                <tr [class.short]="cart.isShort(item.product)">
                  <td>
                    {{ item.product.name }}
                    @if (cart.isShort(item.product)) {
                      <div class="short-note">
                        Stock insuffisant : {{ stockLabel(item.product) }} disponible(s)
                      </div>
                    }
                  </td>
                  <td>
                    <app-cart-line-quantity [cart]="cart" [line]="item" />
                  </td>
                  <td class="num">{{ lineTotal(item) | ariary }}</td>
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <div class="empty">Panier vide — sélectionnez des produits.</div>
        }

        <div class="total-row">
          <span>Total</span>
          <span>{{ cart.total() | ariary }}</span>
        </div>

        @if (cart.hasShortage()) {
          <div class="short-note" role="alert" style="margin:12px 0 8px;">
            Le stock ne suffit pas pour les lignes en rouge : réduisez-les ou retirez-les.
          </div>
        }
        <button
          class="btn block"
          type="button"
          style="padding:11px; margin-top:16px;"
          [disabled]="!canValidate()"
          (click)="openCheckout()"
        >
          Valider la vente <span class="key-hint">(F10)</span>
        </button>
      </div>
    </div>

    <!-- Phone only: under the thumb, what the basket comes to and the two things to do with
         it — look at it, or take the money. Sticky rather than fixed, so it stays inside the
         page on a tablet whose sidebar is still showing. -->
    <div class="sale-bar" [class.short]="cart.hasShortage()">
      <div class="sale-bar-sum">
        <strong>{{ cart.total() | ariary }}</strong>
        <span>{{ formatQuantity(cart.units()) }} article(s)</span>
      </div>
      <button
        class="btn ghost"
        type="button"
        [disabled]="cart.isEmpty() && !held.count()"
        (click)="cartSheetOpen.set(true)"
      >
        <app-icon name="list" [size]="18" />
        Détails
        @if (held.count()) {
          <span class="held-count" [attr.aria-label]="held.count() + ' en attente'">{{ held.count() }}</span>
        }
      </button>
      <button class="btn" type="button" [disabled]="!canValidate()" (click)="openCheckout()">
        Valider
      </button>
    </div>

    @if (checkoutOpen()) {
      <app-sale-checkout-dialog
        [total]="cart.total()"
        [units]="cart.units()"
        [busy]="submitting()"
        [(clientName)]="clientName"
        (confirmed)="record($event)"
        (closed)="closeCheckout()"
      />
    }

    @if (sold(); as invoice) {
      <app-invoice-detail-flow
        [invoice]="invoice"
        [showSeller]="false"
        [notice]="'Vente enregistrée — ' + (invoice.sale.totalAmount | ariary)"
        (closed)="nextSale()"
      />
    }
  `,
})
export class NewSaleComponent {
  private readonly invoices = inject(InvoiceStore);
  private readonly toasts = inject(ToastService);
  protected readonly auth = inject(AuthService);

  /** For whom — asked at checkout, kept here so a sale put on hold keeps it. */
  protected readonly clientName = signal('');
  protected readonly submitting = signal(false);

  /** The checkout questions are on screen. */
  protected readonly checkoutOpen = signal(false);
  /** The sale just recorded, shown with what can be done to it next; null back at the till. */
  protected readonly sold = signal<Invoice | null>(null);
  /** Phone only: the basket brought up as a panel from the bottom bar's "Détails". */
  protected readonly cartSheetOpen = signal(false);
  /** A dialog is up: the till's keys wait, so F4 cannot hold a basket being paid for. */
  private readonly dialogOpen = computed(() => this.checkoutOpen() || this.sold() !== null);

  protected readonly cart = new Cart();
  protected readonly held = inject(HeldSales);

  protected readonly entry: ProductEntry = new ProductEntry(this.cart, () => this.finder.settled());

  protected readonly finder = new ProductFinder(
    computed(() => ({ term: this.entry.term(), categoryId: null })),
  );

  /**
   * The product field keeps the focus: a barcode scanner types wherever the cursor is, and at
   * a till that must always be here.
   */
  private readonly productField = viewChild.required<ElementRef<HTMLInputElement>>('productField');

  /** Not with a line in red: the server would only refuse it again. */
  protected readonly canValidate = computed(
    () => !this.cart.isEmpty() && !this.cart.hasShortage() && !this.dialogOpen(),
  );

  /**
   * The cart is not touched while a sale is being paid for: held at that moment it would be
   * sold and kept waiting at once, and a basket resumed then would be wiped by the success.
   */
  protected readonly canHold = computed(() => !this.cart.isEmpty() && !this.dialogOpen());
  protected readonly canResume = computed(() => this.held.count() > 0 && !this.dialogOpen());

  /**
   * The till's function keys; the legend under the product field is drawn from this list. F10
   * opens the checkout here, and records the sale inside it — so a cashier pays with F10, F10.
   */
  protected readonly keys = new ShortcutMap([
    {
      key: 'F2',
      label: 'Produit',
      run: () => this.focusProductField(),
      enabled: () => !this.dialogOpen(),
    },
    {
      key: 'F4',
      label: 'Mettre en attente',
      run: () => this.holdSale(),
      enabled: () => this.canHold(),
    },
    {
      key: 'F8',
      label: 'Retirer le dernier article',
      run: () => this.removeLastUnit(),
      enabled: () => !this.cart.isEmpty() && !this.dialogOpen(),
    },
    {
      key: 'F9',
      label: 'Reprendre une vente',
      run: () => this.resumeOldest(),
      enabled: () => this.canResume(),
    },
    {
      key: 'F10',
      label: 'Valider la vente',
      run: () => this.openCheckout(),
      enabled: () => this.canValidate(),
    },
  ]);

  protected readonly isEnabled = isEnabled;
  protected readonly stockLabel = stockLabel;
  protected readonly formatQuantity = formatQuantity;
  protected readonly lineTotal = lineTotal;

  /**
   * Returns nothing, on purpose. Angular cancels any event whose handler returns `false`, and
   * {@link ShortcutMap.handle} answers false for every key that is not a shortcut: bound
   * directly, it swallowed every letter typed into the page.
   */
  protected onKeydown(event: KeyboardEvent): void {
    this.keys.handle(event);
  }

  constructor() {
    // Another till selling the same article turns the line short here, before the sale is sent.
    followStock(this.cart);
    afterNextRender(() => this.focusProductField());
  }

  /** A tap on a tile. The field is selected back, so the next scan replaces the search. */
  protected pick(product: Product): void {
    this.cart.add(product);
    this.focusProductField();
  }

  /** The tile's "−": one unit back on the shelf, the line gone at zero. */
  protected unpick(product: Product): void {
    this.cart.change(product, -1);
  }

  /** Sets this customer aside, with their name, and frees the till for the next one. */
  protected holdSale(): void {
    if (!this.canHold()) {
      return;
    }
    const held = this.held.hold(this.cart, this.clientName());
    if (held) {
      this.clientName.set('');
      this.entry.clear();
      this.toasts.show(
        `Vente mise en attente — ${held.clientName || this.auth.words().client.toLowerCase() + ' non précisé(e)'}.`,
      );
    }
    this.focusProductField();
  }

  /**
   * Brings a held sale back to the till. A basket already in progress is not lost: it takes
   * the returning one's place in the waiting list.
   */
  protected resume(id: string): void {
    if (!this.canResume()) {
      return;
    }
    const sale = this.held.take(id);
    if (!sale) {
      return;
    }
    if (!this.cart.isEmpty()) {
      this.held.hold(this.cart, this.clientName());
    }
    this.cart.restore(sale.lines);
    this.clientName.set(sale.clientName);
    this.focusProductField();
  }

  protected unitsOf(sale: HeldSale): number {
    return sale.lines.reduce((sum, line) => sum + line.quantity, 0);
  }

  protected totalOf(sale: HeldSale): number {
    return sale.lines.reduce((sum, line) => sum + lineTotal(withUnit(line)), 0);
  }


  private resumeOldest(): void {
    const oldest = this.held.list()[0];
    if (oldest) {
      this.resume(oldest.id);
    }
  }

  /** One unit off the line scanned last — the usual "not that one" at a till. */
  private removeLastUnit(): void {
    const last = this.cart.lastLine();
    if (last) {
      this.cart.change(last.product, -1);
    }
  }

  /**
   * Not on a phone: there, focusing the field after each tap on a product would pop the
   * keyboard up over the products being tapped. Decided on the layout rather than on the
   * pointer, because a touch-screen till with a barcode scanner is a till all the same: its
   * scanner needs the field focused.
   */
  private focusProductField(): void {
    if (matchMedia(COMPACT_LAYOUT).matches) {
      return;
    }
    const field = this.productField().nativeElement;
    field.focus();
    field.select();
  }

  /** "Valider la vente": the basket is done, the checkout asks for whom and how it is paid. */
  protected openCheckout(): void {
    if (this.canValidate()) {
      // On a phone the basket panel may be up: the checkout takes its place.
      this.cartSheetOpen.set(false);
      this.checkoutOpen.set(true);
    }
  }

  protected closeCheckout(): void {
    this.checkoutOpen.set(false);
    this.focusProductField();
  }

  /**
   * Records the sale as the checkout settled it. On success the till is emptied at once and the
   * new invoice comes up with what can be done next (print, serve…); closing it starts the next
   * sale. A shelf too short sends the cashier back to the basket, where the lines turn red; any
   * other refusal keeps the checkout open, its answers intact.
   */
  protected record(checkout: SaleCheckout): void {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.invoices
      .createSale({ clientName: this.clientName(), ...checkout, lines: this.cart.toSaleLines() })
      .subscribe({
        next: (invoice) => {
          this.submitting.set(false);
          this.checkoutOpen.set(false);
          this.cart.clear();
          this.entry.clear();
          this.clientName.set('');
          // The server has moved the stock; re-read it rather than guess the new figures.
          this.finder.refresh();
          this.sold.set(invoice);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          const shortages = stockShortagesOf(error);
          if (shortages.length) {
            this.cart.applyStock(shortages);
            this.checkoutOpen.set(false);
          }
          // The interceptor has already shown why; refresh in case stock moved elsewhere.
          this.finder.refresh();
        },
      });
  }

  /** The invoice of the last sale is closed: back to an empty till, ready to scan. */
  protected nextSale(): void {
    this.sold.set(null);
    this.focusProductField();
  }
}

/** A held line from before units had none: it was in the base unit. */
function withUnit(line: CartLine): CartLine {
  return line.unit ? line : { ...line, unit: baseUnitOf(line.product) };
}
