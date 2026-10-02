import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { OnlineOrderApi } from '../../core/api/online-order.api';
import {
  MAX_ONLINE_UNITS_PER_LINE,
  PublicMenu,
  PublicMenuItem,
  PublicOrder,
  PublicOrderStatus,
} from '../../core/models';
import { formatAmount } from '../../core/sale/sale-unit';
import { ToastService } from '../../core/services/toast.service';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

/** How often a sent order's status is asked again: the customer is waiting, not watching. */
const POLL_MS = 10_000;

/** A unit the customer can order in; `id` null for the base unit. */
interface OrderUnit {
  readonly id: number | null;
  readonly label: string | null;
  readonly price: number;
}

/** One item of the basket in one unit. */
interface OrderLine {
  readonly item: PublicMenuItem;
  readonly unit: OrderUnit;
  readonly quantity: number;
}

const STATUS_LABELS: Record<PublicOrderStatus, string> = {
  RECEIVED: 'Reçue — en préparation',
  SERVED: 'Servie',
  CANCELLED: 'Annulée',
};

/**
 * The page a table's QR code opens, on the customer's phone: the menu, a basket, and the orders
 * sent from this table so far with where each one is.
 *
 * <p>Answered on the shop's Wi-Fi only (the server refuses the Internet): a phone outside is
 * told to join the Wi-Fi rather than shown an error.
 *
 * <p>No account and no shell: the table is in the link, the name is optional, nothing is paid
 * here — the waiter takes the money when serving, or the customer pays at the till. The orders
 * sent are remembered for the browser tab, so a reload does not lose track of them. Their status
 * is polled: the live socket needs an account, and a customer has none.
 */
@Component({
  selector: 'app-table-order',
  standalone: true,
  imports: [FormsModule, AriaryPipe],
  template: `
    <header class="public-bar">
      <div class="mark">Libero <span>Shop</span></div>
      @if (menu(); as m) {
        <div class="table-name">{{ m.tableName }}</div>
      }
    </header>

    <main class="public-content">
      @if (failed() === 'outside') {
        <div class="card empty-state">
          <h2>Connectez-vous au Wi-Fi</h2>
          <p>
            La commande se fait sur place : connectez votre téléphone au Wi-Fi de l'établissement,
            puis scannez à nouveau le QR code de votre table.
          </p>
        </div>
      } @else if (failed()) {
        <div class="card empty-state">
          <h2>Commande indisponible</h2>
          <p>Ce QR code n'est plus valable, ou la commande en ligne est fermée. Demandez au serveur.</p>
        </div>
      } @else if (menu()) {
        @if (sent().length) {
          <section class="card sent">
            <h2>Vos commandes</h2>
            @for (order of sent(); track order.invoiceNumber) {
              <div class="sent-order">
                <div class="line">
                  <strong>{{ order.invoiceNumber }}</strong>
                  <span class="status" [class]="'status ' + order.status.toLowerCase()">
                    {{ statusLabel(order.status) }}
                  </span>
                </div>
                <div class="muted small">
                  @for (l of order.lines; track $index) {
                    {{ formatAmount(l.quantity, l.unit) }}{{ l.unit ? '' : '×' }} {{ l.name }}{{ $last ? '' : ', ' }}
                  }
                </div>
                <div class="line">
                  <span>{{ order.total | ariary }}</span>
                  <span class="muted small">
                    {{ order.paid ? 'Payée' : 'À régler au serveur ou à la caisse' }}
                  </span>
                </div>
              </div>
            }
          </section>
        }

        <div class="order-top">
          <div class="fld who">
            <label for="customer-name">Votre nom (facultatif)</label>
            <input
              id="customer-name"
              type="text"
              autocomplete="given-name"
              maxlength="40"
              placeholder="Pour vous appeler"
              [ngModel]="customerName()"
              (ngModelChange)="customerName.set($event)"
            />
          </div>
          <div class="fld what">
            <label for="menu-search">Rechercher</label>
            <input
              id="menu-search"
              type="search"
              autocomplete="off"
              placeholder="Plat, boisson…"
              [ngModel]="search()"
              (ngModelChange)="search.set($event)"
            />
          </div>
        </div>

        @if (categories().length > 1) {
          <div class="chips" role="tablist" aria-label="Catégories">
            <button type="button" class="chip" [class.active]="category() === null" (click)="category.set(null)">
              Tout
            </button>
            @for (name of categories(); track name) {
              <button type="button" class="chip" [class.active]="category() === name" (click)="category.set(name)">
                {{ name }}
              </button>
            }
          </div>
        }

        <div class="tiles">
          @for (item of visibleItems(); track item.id) {
            <button
              type="button"
              class="tile"
              [class.picked]="quantityOf(item) > 0"
              [disabled]="!item.available || baseQuantityOf(item) >= maxUnits"
              (click)="change(item, baseUnitOf(item), 1)"
            >
              @if (quantityOf(item) > 0) {
                <span class="count">{{ quantityOf(item) }}</span>
              }
              <span class="name">{{ item.name }}</span>
              <span class="price">
                {{ item.price | ariary }}@if (item.unit) {<span class="muted"> / {{ item.unit }}</span>}
              </span>
              @if (!item.available) {
                <span class="out">Épuisé</span>
              }
            </button>
          } @empty {
            <div class="empty">Rien ne correspond à la recherche.</div>
          }
        </div>

        <div class="order-bar">
          @if (lines().length) {
            <button type="button" class="summary" (click)="reviewing.set(true)">
              <strong>{{ units() }} article(s)</strong>
              <span>{{ total() | ariary }} · voir</span>
            </button>
            <button type="button" class="btn send" (click)="reviewing.set(true)">Commander ›</button>
          } @else {
            <span class="muted hint">Touchez un plat ou une boisson pour l'ajouter.</span>
          }
        </div>
      } @else {
        <div class="empty">Chargement de la carte…</div>
      }
    </main>

    @if (reviewing()) {
      <div class="modal-backdrop" (click)="onBackdrop($event)">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="public-review-title">
          <div class="modal-head">
            <div>
              <h2 id="public-review-title">Vérifiez votre commande</h2>
              <div class="sub muted">
                {{ menu()?.tableName }}{{ customerName().trim() ? ' · ' + customerName().trim() : '' }}
              </div>
            </div>
            <button class="x" type="button" aria-label="Fermer" (click)="reviewing.set(false)">✕</button>
          </div>
          <div class="modal-body">
            <ul class="review">
              @for (line of lines(); track keyOf(line.item.id, line.unit.id)) {
                <li>
                  <span class="name">
                    {{ line.item.name }}
                    <!-- One button per unit it can be ordered in: a tap orders the line in it. -->
                    @if (line.item.units.length) {
                      <span class="units" role="group" aria-label="Unité">
                        @for (unit of unitsOf(line.item); track unit.id) {
                          <button
                            type="button"
                            class="unit-chip"
                            [class.on]="unit.id === line.unit.id"
                            [attr.aria-pressed]="unit.id === line.unit.id"
                            (click)="setUnit(line, unit)"
                          >
                            {{ unit.label ?? 'unité' }}
                          </button>
                        }
                      </span>
                    }
                  </span>
                  <span class="qty">
                    <button class="qtybtn" type="button" aria-label="Retirer un" (click)="change(line.item, line.unit, -1)">−</button>
                    {{ line.quantity }}
                    <button
                      class="qtybtn"
                      type="button"
                      aria-label="Ajouter un"
                      [disabled]="line.quantity >= maxUnits"
                      (click)="change(line.item, line.unit, 1)"
                    >
                      +
                    </button>
                  </span>
                  <span class="sub-total">{{ line.unit.price * line.quantity | ariary }}</span>
                </li>
              } @empty {
                <li class="muted">La commande est vide.</li>
              }
            </ul>
            <div class="total-row">
              <span>Total</span>
              <span>{{ total() | ariary }}</span>
            </div>
            <p class="muted small" style="margin:12px 0 0;">
              Le paiement se fait au serveur, à la livraison, ou à la caisse.
            </p>
          </div>
          <div class="modal-foot send-actions">
            <button class="btn ghost" type="button" (click)="reviewing.set(false)">Modifier</button>
            <button class="btn" type="button" [disabled]="!lines().length || sending()" (click)="send()">
              {{ sending() ? 'Envoi…' : 'Confirmer la commande' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
      background: var(--bg, #f3f5f2);
    }

    .units {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      margin-top: 4px;
    }

    .unit-chip {
      border: 1px solid var(--line);
      border-radius: 20px;
      background: none;
      color: var(--ink-soft);
      font: inherit;
      font-size: 12px;
      padding: 3px 10px;
      cursor: pointer;

      &.on {
        border-color: var(--brand);
        background: var(--brand-soft);
        color: var(--brand-dark);
        font-weight: 600;
      }
    }

    .public-bar {
      position: sticky;
      top: 0;
      z-index: 40;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 12px 16px;
      background: var(--chrome-bg);
      color: var(--chrome-ink);

      .mark {
        font-weight: 700;
        font-size: 17px;

        span {
          color: var(--chrome-accent);
        }
      }

      .table-name {
        font-weight: 700;
        font-size: 16px;
        padding: 4px 12px;
        border-radius: 14px;
        background: rgba(255, 255, 255, 0.14);
      }
    }

    .public-content {
      max-width: 760px;
      margin: 0 auto;
      padding: 14px 14px 0;
    }

    .empty-state {
      text-align: center;
    }

    .small {
      font-size: 12.5px;
    }

    .sent {
      margin-bottom: 14px;

      h2 {
        margin-bottom: 6px;
      }
    }

    .sent-order {
      padding: 10px 0;
      border-bottom: 1px solid var(--line);

      &:last-child {
        border-bottom: none;
      }

      .line {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 10px;
      }
    }

    .status {
      font-size: 12.5px;
      font-weight: 600;
      padding: 3px 10px;
      border-radius: 12px;
      background: var(--amber-soft);
      color: var(--amber);

      &.served {
        background: var(--brand-soft);
        color: var(--brand-dark);
      }

      &.cancelled {
        background: var(--red-soft);
        color: var(--red);
      }
    }
  `,
})
export class TableOrderComponent implements OnInit {
  private readonly api = inject(OnlineOrderApi);
  private readonly toasts = inject(ToastService);
  private readonly token: string = inject(ActivatedRoute).snapshot.paramMap.get('token') ?? '';
  private readonly destroyRef = inject(DestroyRef);

  protected readonly maxUnits = MAX_ONLINE_UNITS_PER_LINE;
  protected readonly menu = signal<PublicMenu | null>(null);
  /** Why the menu could not be shown: off the shop's Wi-Fi, or a code that no longer works. */
  protected readonly failed = signal<'outside' | 'closed' | null>(null);
  protected readonly search = signal('');
  protected readonly category = signal<string | null>(null);
  protected readonly customerName = signal('');
  protected readonly reviewing = signal(false);
  protected readonly sending = signal(false);
  /** The orders sent from this tab, newest first. */
  protected readonly sent = signal<PublicOrder[]>([]);

  /** By {@link keyOf}: one item in one unit. */
  private readonly quantities = signal<Record<string, number>>({});
  private readonly storageKey = `liberoshop.table-orders.${this.token}`;

  protected readonly categories = computed(() =>
    [...new Set((this.menu()?.items ?? []).map((i) => i.category).filter((c): c is string => !!c))].sort(
      (a, b) => a.localeCompare(b, 'fr'),
    ),
  );

  protected readonly visibleItems = computed(() => {
    const term = this.search().trim().toLowerCase();
    const category = this.category();
    return (this.menu()?.items ?? []).filter(
      (i) =>
        (category === null || i.category === category) &&
        (!term || i.name.toLowerCase().includes(term) || (i.category ?? '').toLowerCase().includes(term)),
    );
  });

  protected readonly lines = computed<OrderLine[]>(() =>
    (this.menu()?.items ?? []).flatMap((item) =>
      this.unitsOf(item)
        .map((unit) => ({ item, unit, quantity: this.quantities()[this.keyOf(item.id, unit.id)] ?? 0 }))
        .filter((line) => line.quantity > 0),
    ),
  );
  protected readonly units = computed(() => this.lines().reduce((s, l) => s + l.quantity, 0));
  protected readonly total = computed(() => this.lines().reduce((s, l) => s + l.unit.price * l.quantity, 0));
  protected readonly formatAmount = formatAmount;

  ngOnInit(): void {
    this.api.menu(this.token).subscribe({
      next: (menu) => this.menu.set(menu),
      error: (error: HttpErrorResponse) => this.failed.set(error.status === 403 ? 'outside' : 'closed'),
    });
    this.restoreSent();
    const timer = setInterval(() => this.refreshSent(), POLL_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
  }

  protected statusLabel(status: PublicOrderStatus): string {
    return STATUS_LABELS[status];
  }

  /** One item in one unit; `null` is the base unit. */
  protected keyOf(itemId: number, unitId: number | null): string {
    return `${itemId}:${unitId ?? 'base'}`;
  }

  protected baseUnitOf(item: PublicMenuItem): OrderUnit {
    return { id: null, label: item.unit, price: item.price };
  }

  /** The base unit first, then the others, smallest first. */
  protected unitsOf(item: PublicMenuItem): OrderUnit[] {
    return [this.baseUnitOf(item), ...item.units];
  }

  /** Every unit of it together, for the tile's badge. */
  protected quantityOf(item: PublicMenuItem): number {
    return this.unitsOf(item).reduce(
      (sum, unit) => sum + (this.quantities()[this.keyOf(item.id, unit.id)] ?? 0),
      0,
    );
  }

  protected baseQuantityOf(item: PublicMenuItem): number {
    return this.quantities()[this.keyOf(item.id, null)] ?? 0;
  }

  protected change(item: PublicMenuItem, unit: OrderUnit, delta: number): void {
    const key = this.keyOf(item.id, unit.id);
    this.put(key, (this.quantities()[key] ?? 0) + delta);
  }

  /** Orders the line in another unit, keeping the number, joined to that unit's line if any. */
  protected setUnit(line: OrderLine, unit: OrderUnit): void {
    if (unit.id === line.unit.id) {
      return;
    }
    const target = this.keyOf(line.item.id, unit.id);
    const merged = (this.quantities()[target] ?? 0) + line.quantity;
    this.put(this.keyOf(line.item.id, line.unit.id), 0);
    this.put(target, merged);
  }

  /** At most {@link maxUnits} a line, like the server; at zero the line goes. */
  private put(key: string, quantity: number): void {
    this.quantities.update((current) => {
      const next = Math.min(quantity, this.maxUnits);
      const updated = { ...current };
      if (next <= 0) {
        delete updated[key];
      } else {
        updated[key] = next;
      }
      return updated;
    });
  }

  protected send(): void {
    const lines = this.lines();
    if (!lines.length || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.api
      .order(this.token, {
        clientName: this.customerName().trim() || null,
        lines: lines.map((l) => ({ productId: l.item.id, packagingId: l.unit.id, quantity: l.quantity })),
      })
      .subscribe({
        next: (order) => {
          this.sending.set(false);
          this.reviewing.set(false);
          this.quantities.set({});
          this.search.set('');
          this.sent.update((orders) => [order, ...orders]);
          this.storeSent();
          window.scrollTo({ top: 0, behavior: 'smooth' });
          this.toasts.show(`Commande ${order.invoiceNumber} envoyée : le serveur arrive.`);
        },
        // The interceptor has shown the server's reason (too soon, sold out…).
        error: () => this.sending.set(false),
      });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.reviewing.set(false);
    }
  }

  /** Asks again about the orders still moving; a cancelled one, or a served and paid one, is done. */
  private refreshSent(): void {
    for (const order of this.sent()) {
      if (order.status === 'CANCELLED' || (order.status === 'SERVED' && order.paid)) {
        continue;
      }
      this.api.status(this.token, order.invoiceNumber).subscribe({
        next: (fresh) => {
          this.sent.update((orders) => orders.map((o) => (o.invoiceNumber === fresh.invoiceNumber ? fresh : o)));
          this.storeSent();
        },
        error: () => {},
      });
    }
  }

  private restoreSent(): void {
    try {
      const stored = JSON.parse(sessionStorage.getItem(this.storageKey) ?? '[]') as PublicOrder[];
      this.sent.set(stored);
      this.refreshSent();
    } catch {
      sessionStorage.removeItem(this.storageKey);
    }
  }

  private storeSent(): void {
    sessionStorage.setItem(this.storageKey, JSON.stringify(this.sent()));
  }
}
