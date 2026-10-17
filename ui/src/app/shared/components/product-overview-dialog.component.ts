import { DatePipe } from '@angular/common';
import { Component, ElementRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { CostingApi } from '../../core/api/costing.api';
import { StockApi } from '../../core/api/stock.api';
import { actionRows, recentReceipts } from '../../core/catalog/product-overview';
import { ProductMenuId, productMenu, requestFor } from '../../core/catalog/product-menu';
import { ProductDialogRequest, ProductRights } from '../../core/catalog/product-rights';
import { Product, ProductCost, ProductMovement } from '../../core/models';
import { formatAmount, stockLabel } from '../../core/sale/sale-unit';
import { isTopmostDialog } from '../../core/ui/topmost-dialog';
import { AriaryPipe } from '../pipes/ariary.pipe';

/**
 * A product at a glance, opened by touching its name: stock, prices and profit per unit, its last
 * supplier and receipts — read only. What its "⋮" offers is repeated below as buttons, easier to
 * reach on a phone than the small "⋮".
 */
@Component({
  selector: 'app-product-overview-dialog',
  standalone: true,
  imports: [DatePipe, AriaryPipe],
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="po-title">
        <div class="modal-head">
          <div>
            <h2 id="po-title">{{ product().name }}</h2>
            <div class="sub muted">
              {{ product().category?.name ?? 'Sans rayon' }}
              @if (product().retired) {
                · <span class="badge">Retiré de la vente</span>
              }
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          <div class="po-stock" [class.po-low]="product().lowStock">
            <span class="muted">En stock</span>
            <strong>{{ stock() }}</strong>
            @if (product().packagings?.length) {
              <span class="muted">≈ {{ inBestUnit() }}</span>
            }
            @if (product().lowStock) {
              <span class="po-alert">sous le seuil d'alerte</span>
            }
          </div>

          @if (cost(); as cost) {
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Unité</th>
                    <th class="num">Prix de vente</th>
                    <th class="num">Coût</th>
                    <th class="num">Bénéfice</th>
                  </tr>
                </thead>
                <tbody>
                  @for (unit of cost.units; track unit.packagingId) {
                    <tr>
                      <td>
                        {{ unit.label ?? 'unité' }}
                        @if (unit.packagingId === null) {
                          <small class="muted">(unité de base)</small>
                        }
                      </td>
                      <td class="num">{{ unit.salePrice | ariary }}</td>
                      <td class="num">{{ unit.cost === null ? '—' : (unit.cost | ariary) }}</td>
                      <td class="num" [class.po-loss]="unit.margin !== null && unit.margin < 0">
                        @if (unit.margin === null) {
                          <span class="muted">prix d'entrée inconnu</span>
                        } @else {
                          {{ unit.margin | ariary }} <small class="muted">({{ unit.marginRate }} %)</small>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (cost.lastSupplierName) {
              <p class="muted po-line">
                Dernier prix payé chez <strong>{{ cost.lastSupplierName }}</strong>
                @if (cost.lastPurchaseDate) {
                  le {{ cost.lastPurchaseDate | date: 'dd/MM/yyyy' }}
                }
              </p>
            }
          } @else {
            <div class="empty">Chargement…</div>
          }

          <h3 class="po-h">Dernières entrées</h3>
          @if (receipts().length) {
            <ul class="po-receipts">
              @for (receipt of receipts(); track receipt.id) {
                <li>
                  <span class="muted">{{ receipt.movementDate | date: 'dd/MM/yy' }}</span>
                  <strong>+{{ amount(receipt.quantity) }}</strong>
                  <span class="muted">{{ receipt.supplierName ?? 'Sans fournisseur' }}</span>
                </li>
              }
            </ul>
          } @else if (historyLoaded()) {
            <p class="muted po-line">Aucune entrée de stock pour l'instant.</p>
          }

          @for (row of rows(); track row.group) {
            <div class="po-actions" role="group" [attr.aria-label]="row.group">
              <span class="po-group muted">{{ row.group }}</span>
              @for (entry of row.entries; track entry.id) {
                <button
                  class="btn small ghost"
                  type="button"
                  [class.po-danger]="entry.danger"
                  (click)="pick(entry.id)"
                >
                  {{ entry.label }}
                </button>
              }
            </div>
          }
        </div>

        <div class="modal-foot">
          <button class="btn ghost" type="button" (click)="closed.emit()">Fermer</button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .po-stock {
      display: flex;
      align-items: baseline;
      gap: 10px;
      flex-wrap: wrap;
      margin-bottom: 12px;

      strong {
        font-size: 20px;
      }
    }

    .po-alert,
    .po-loss,
    .po-danger {
      color: var(--red);
    }

    .po-line {
      margin: 8px 0 0;
      font-size: 13.5px;
    }

    .po-h {
      font-size: 14px;
      margin: 16px 0 6px;
    }

    .po-receipts {
      list-style: none;
      margin: 0;
      padding: 0;
      font-size: 13.5px;

      li {
        display: flex;
        gap: 10px;
        padding: 3px 0;
      }
    }

    .po-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
      margin-top: 12px;
    }

    .po-group {
      flex-basis: 100%;
      font-size: 12.5px;
    }
  `,
})
export class ProductOverviewDialogComponent implements OnInit {
  private readonly hostElement = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly costing = inject(CostingApi);
  private readonly stockApi = inject(StockApi);
  private readonly rights = inject(ProductRights);

  readonly product = input.required<Product>();
  /** A button picked: the dialog it opens, in place of this one. */
  readonly picked = output<ProductDialogRequest>();
  readonly closed = output<void>();

  protected readonly cost = signal<ProductCost | null>(null);
  private readonly history = signal<ProductMovement[] | null>(null);

  protected readonly stock = computed(() => formatAmount(this.product().stockQuantity, this.product().unit));
  protected readonly inBestUnit = computed(() => stockLabel(this.product()));
  protected readonly receipts = computed(() => recentReceipts(this.history() ?? []));
  protected readonly historyLoaded = computed(() => this.history() !== null);
  protected readonly rows = computed(() => actionRows(productMenu(this.product(), this.rights.menuRights())));

  ngOnInit(): void {
    const id = this.product().id;
    this.costing.productCost(id).subscribe((cost) => this.cost.set(cost));
    this.stockApi.productMovements(id, 0).subscribe((page) => this.history.set(page.items));
  }

  protected amount(quantity: number): string {
    return formatAmount(quantity, this.product().unit);
  }

  protected pick(id: ProductMenuId): void {
    this.picked.emit(requestFor(id, this.product()));
  }

  protected onEscape(): void {
    if (isTopmostDialog(this.hostElement.nativeElement)) {
      this.closed.emit();
    }
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
