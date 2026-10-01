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
import { InvoiceApi } from '../../core/api/invoice.api';
import { PAYMENT_METHOD_LABELS, PaymentMethod, PaymentStatus, Product } from '../../core/models';
import { Cart } from '../../core/sale/cart';
import { HeldSale, HeldSales } from '../../core/sale/held-sales';
import { ProductEntry } from '../../core/sale/product-entry';
import { ProductFinder } from '../../core/sale/product-finder';
import { ShortcutMap, isEnabled } from '../../core/sale/shortcuts';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { CameraScanButtonComponent } from '../../shared/components/camera-scan-button.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

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
  imports: [FormsModule, AriaryPipe, DatePipe, CameraScanButtonComponent],
  host: { '(document:keydown)': 'keys.handle($event)' },
  template: `
    <div class="grid g2">
      <div class="card">
        <h2>Produits <small>scannez, tapez un code puis Entrée, ou cliquez</small></h2>
        <div class="form-row" style="align-items:flex-end;">
          <div class="fld" style="flex:1; min-width:220px;">
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
        <div class="muted" style="font-size:12px; margin:0 0 8px; display:flex; flex-wrap:wrap; gap:4px 12px;">
          @for (shortcut of keys.shortcuts; track shortcut.key) {
            <span [style.opacity]="isEnabled(shortcut) ? 1 : 0.45">
              <kbd>{{ shortcut.key }}</kbd> {{ shortcut.label }}
            </span>
          }
        </div>
        <div class="muted" style="font-size:12px; margin:0 0 8px;">
          {{ finder.idle() ? 'Les plus vendus ces 30 derniers jours' : 'Résultats de la recherche' }}
          @if (finder.pending()) {
            <span> — recherche…</span>
          }
        </div>
        <div class="prod-grid">
          @for (product of finder.products(); track product.id) {
            <button
              type="button"
              class="prod-card"
              [class.low]="product.lowStock"
              [disabled]="cart.remainingStock(product) <= 0"
              (click)="pick(product)"
            >
              <div class="pname">{{ product.name }}</div>
              <div class="pcat">{{ product.category?.name ?? 'Sans catégorie' }}</div>
              <div class="pprice">{{ product.price | ariary }}</div>
              <div class="pstock">Disponible : {{ cart.remainingStock(product) }}</div>
            </button>
          } @empty {
            @if (!finder.pending()) {
              <div class="empty">
                {{ finder.idle() ? 'Aucun produit au catalogue.' : 'Aucun produit ne correspond à la recherche.' }}
              </div>
            }
          }
        </div>
      </div>

      <div class="card">
        <h2>
          Panier
          <button
            class="btn ghost"
            type="button"
            style="float:right; padding:4px 10px; font-size:12px;"
            [disabled]="!canHold()"
            (click)="holdSale()"
          >
            Mettre en attente (F4)
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
                ⏸ {{ sale.clientName || 'Client' }} · {{ unitsOf(sale) }} art. · {{ totalOf(sale) | ariary }}
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
              @for (item of cart.lines(); track item.product.id) {
                <tr>
                  <td>{{ item.product.name }}</td>
                  <td>
                    <button class="qtybtn" type="button" (click)="cart.change(item.product, -1)">
                      −
                    </button>
                    {{ item.quantity }}
                    <button
                      class="qtybtn"
                      type="button"
                      [disabled]="item.quantity >= item.product.stockQuantity"
                      (click)="cart.change(item.product, 1)"
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
          <span>{{ cart.total() | ariary }}</span>
        </div>

        <div class="form-row" style="margin-top:16px;">
          <div class="fld" style="flex:1; min-width:180px;">
            <label for="client-name">{{ auth.words().client }}</label>
            <input
              id="client-name"
              type="text"
              [placeholder]="auth.words().clientPlaceholder"
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
              <option [ngValue]="PaymentStatus.UNPAID">{{ unpaidLabel() }}</option>
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
          [disabled]="!canValidate()"
          (click)="validateSale()"
        >
          {{ submitting() ? 'Enregistrement...' : 'Valider la vente et générer la facture (F10)' }}
        </button>
      </div>
    </div>
  `,
})
export class NewSaleComponent {
  private readonly invoices = inject(InvoiceApi);
  private readonly toasts = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly PaymentStatus = PaymentStatus;

  /** Where an unpaid order will be settled, which the shop's configuration decides. */
  protected readonly unpaidLabel = computed(() =>
    this.auth.settings().payAtDepot
      ? `Non payée (à régler à la remise — ${this.auth.words().depot.toLowerCase()})`
      : 'Non payée (à encaisser plus tard)',
  );
  protected readonly paymentMethods = Object.values(PaymentMethod);
  protected readonly methodLabels = PAYMENT_METHOD_LABELS;

  protected readonly clientName = signal('');
  protected readonly paymentStatus = signal<PaymentStatus>(PaymentStatus.PAID);
  protected readonly paymentMethod = signal<PaymentMethod>(PaymentMethod.CASH);
  protected readonly submitting = signal(false);

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

  protected readonly canValidate = computed(() => !this.cart.isEmpty() && !this.submitting());

  /**
   * The cart is not touched while a sale is being recorded: held at that moment it would be
   * sold and kept waiting at once, and a basket resumed then would be wiped by the success.
   */
  protected readonly canHold = computed(() => !this.cart.isEmpty() && !this.submitting());
  protected readonly canResume = computed(() => this.held.count() > 0 && !this.submitting());

  /** The till's function keys; the legend under the product field is drawn from this list. */
  protected readonly keys = new ShortcutMap([
    { key: 'F2', label: 'Produit', run: () => this.focusProductField() },
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
      enabled: () => !this.cart.isEmpty(),
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
      run: () => this.validateSale(),
      enabled: () => this.canValidate(),
    },
  ]);

  protected readonly isEnabled = isEnabled;

  constructor() {
    afterNextRender(() => this.focusProductField());
  }

  /** A tap on a tile. The field is selected back, so the next scan replaces the search. */
  protected pick(product: Product): void {
    this.cart.add(product);
    this.focusProductField();
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
      this.toasts.show(`Vente mise en attente — ${held.clientName || 'client sans nom'}.`);
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
    return sale.lines.reduce((sum, line) => sum + line.product.price * line.quantity, 0);
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

  private focusProductField(): void {
    const field = this.productField().nativeElement;
    field.focus();
    field.select();
  }

  protected validateSale(): void {
    if (!this.canValidate()) {
      return;
    }
    this.submitting.set(true);

    this.invoices
      .createSale({
        clientName: this.clientName(),
        paymentStatus: this.paymentStatus(),
        paymentMethod: this.paymentMethod(),
        lines: this.cart.toSaleLines(),
      })
      .subscribe({
        next: (invoice) => {
          this.submitting.set(false);
          this.cart.clear();
          this.entry.clear();
          this.clientName.set('');
          this.focusProductField();
          // The server has moved the stock; re-read it rather than guess the new figures.
          this.finder.refresh();
          this.toasts.show(
            `Vente enregistrée — facture ${invoice.invoiceNumber} ` +
              `(${invoice.sale.totalAmount.toLocaleString('fr-FR')} Ar)`,
          );
        },
        error: () => {
          this.submitting.set(false);
          // The interceptor has already shown why; refresh in case stock moved elsewhere.
          this.finder.refresh();
        },
      });
  }
}
