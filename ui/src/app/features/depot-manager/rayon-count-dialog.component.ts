import { Component, ElementRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { ApiResource, apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { StockApi } from '../../core/api/stock.api';
import { CategoryNode, Product } from '../../core/models';
import { formatAmount, formatQuantity } from '../../core/sale/sale-unit';
import { ToastService } from '../../core/services/toast.service';
import { InventoryGap, readCount, summarize } from '../../core/stock/inventory';
import { isTopmostDialog } from '../../core/ui/topmost-dialog';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';
import { HelpButtonComponent } from '../../shared/components/help-button.component';

/**
 * "Compter ce rayon": the shelf counted product by product, the gaps summed up before anything
 * is written, then one inventory correction per product found different — all or none.
 *
 * <p>A product left blank is not counted and not touched: a rayon can be counted in part.
 */
@Component({
  selector: 'app-rayon-count-dialog',
  standalone: true,
  imports: [HelpButtonComponent, AriaryPipe],
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal wide" role="dialog" aria-modal="true" aria-labelledby="rc-title">
        <div class="modal-head">
          <div>
            <h2 id="rc-title">Compter le rayon « {{ rayon().name }} »</h2>
            <div class="sub muted">
              @if (reviewing()) {
                Vérifiez les écarts avant de les enregistrer.
              } @else {
                Tapez ce que vous trouvez sur l'étagère, en unité de base. Laissé vide : pas compté, pas touché.
              }
            </div>
          </div>
          <app-help topic="rayon-count" />
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          @if (!reviewing()) {
            @if (resource.value().length) {
              <div class="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Produit</th>
                      <th class="num">Stock</th>
                      <th class="num">Compté</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (product of resource.value(); track product.id) {
                      <tr>
                        <td>{{ product.name }}</td>
                        <td class="num muted">{{ formatAmount(product.stockQuantity, product.unit) }}</td>
                        <td class="num">
                          <input
                            class="rc-count"
                            type="text"
                            inputmode="decimal"
                            autocomplete="off"
                            [attr.aria-label]="'Compté : ' + product.name"
                            [class.rc-bad]="isUnreadable(product)"
                            [value]="typed().get(product.id) ?? ''"
                            (input)="type(product, $any($event.target).value)"
                          />
                        </td>
                        <td>
                          <!-- The shelf matches: one tap rather than the figure typed again. -->
                          <button class="btn small ghost" type="button" (click)="asStock(product)">= stock</button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            } @else if (resource.loaded()) {
              <div class="empty">Aucun produit dans ce rayon.</div>
            } @else {
              <div class="empty">Chargement…</div>
            }
          } @else {
            <p class="rc-said">
              {{ summary().counted }} produit(s) compté(s) :
              <strong>{{ summary().gaps.length }} écart(s)</strong>,
              {{ summary().counted - summary().gaps.length }} conforme(s).
              @if (summary().skipped) {
                {{ summary().skipped }} laissé(s) vide(s), non touché(s).
              }
            </p>
            @if (summary().gaps.length) {
              <div class="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Produit</th>
                      <th class="num">Stock</th>
                      <th class="num">Compté</th>
                      <th class="num">Écart</th>
                      <th class="num">Valeur</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (gap of summary().gaps; track gap.product.id) {
                      <tr>
                        <td>{{ gap.product.name }}</td>
                        <td class="num muted">{{ formatAmount(gap.product.stockQuantity, gap.product.unit) }}</td>
                        <td class="num">{{ formatAmount(gap.counted, gap.product.unit) }}</td>
                        <td class="num" [class.rc-loss]="gap.difference < 0">{{ signed(gap) }}</td>
                        <td class="num" [class.rc-loss]="gap.value < 0">{{ gap.value | ariary }}</td>
                      </tr>
                    }
                  </tbody>
                  <tfoot>
                    <tr>
                      <th colspan="4">Total des écarts, au prix de vente</th>
                      <th class="num" [class.rc-loss]="totalValue() < 0">{{ totalValue() | ariary }}</th>
                    </tr>
                  </tfoot>
                </table>
              </div>
            } @else {
              <div class="empty">Tout est conforme : aucune correction à écrire.</div>
            }
          }
          @if (summary().unreadable.length) {
            <p class="rc-loss">
              Compte illisible pour : {{ unreadableNames() }}. Corrigez-le avant de continuer.
            </p>
          }
        </div>

        <div class="modal-foot">
          @if (reviewing()) {
            <button class="btn ghost" type="button" (click)="reviewing.set(false)">Revenir au comptage</button>
            <button
              class="btn"
              type="button"
              [disabled]="busy() || !summary().gaps.length"
              (click)="save()"
            >
              {{ busy() ? 'Enregistrement...' : 'Enregistrer ' + summary().gaps.length + ' correction(s)' }}
            </button>
          } @else {
            <button class="btn ghost" type="button" (click)="closed.emit()">Annuler</button>
            <button
              class="btn"
              type="button"
              [disabled]="!summary().counted || summary().unreadable.length > 0"
              (click)="reviewing.set(true)"
            >
              Voir les écarts
            </button>
          }
        </div>
      </div>
    </div>
  `,
  styles: `
    .rc-count {
      width: 100px;
      text-align: right;
    }

    .rc-bad {
      border-color: var(--red);
    }

    .rc-loss {
      color: var(--red);
    }

    .rc-said {
      margin: 0 0 10px;
    }
  `,
})
export class RayonCountDialogComponent implements OnInit {
  private readonly catalog = inject(CatalogApi);
  private readonly stock = inject(StockApi);
  private readonly toasts = inject(ToastService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly rayon = input.required<CategoryNode>();
  /** Corrections written: the stock figures are out of date. */
  readonly changed = output<void>();
  readonly closed = output<void>();

  /** Made once the rayon is known: a resource loads as soon as it exists. */
  protected resource!: ApiResource<Product[]>;
  protected readonly typed = signal<ReadonlyMap<number, string>>(new Map());
  protected readonly reviewing = signal(false);
  protected readonly busy = signal(false);

  protected readonly summary = computed(() => summarize(this.resource.value(), this.typed()));
  protected readonly totalValue = computed(() => this.summary().gaps.reduce((total, gap) => total + gap.value, 0));
  protected readonly unreadableNames = computed(() =>
    this.summary().unreadable.map((product) => product.name).join(', '),
  );
  protected readonly formatAmount = formatAmount;

  ngOnInit(): void {
    this.resource = apiResource<Product[]>([], () => this.catalog.products({ categoryId: this.rayon().id }));
  }

  protected type(product: Product, text: string): void {
    this.typed.update((typed) => new Map(typed).set(product.id, text));
  }

  protected asStock(product: Product): void {
    this.type(product, formatQuantity(product.stockQuantity));
  }

  protected isUnreadable(product: Product): boolean {
    const text = (this.typed().get(product.id) ?? '').trim();
    return text !== '' && readCount(text) === null;
  }

  protected signed(gap: InventoryGap): string {
    return `${gap.difference > 0 ? '+' : '−'}${formatAmount(Math.abs(gap.difference), gap.product.unit)}`;
  }

  protected save(): void {
    const gaps = this.summary().gaps;
    this.busy.set(true);
    this.stock
      .registerInventory({
        categoryId: this.rayon().id,
        lines: gaps.map((gap) => ({ productId: gap.product.id, counted: gap.counted })),
      })
      .subscribe({
        next: (written) => {
          this.toasts.show(`Inventaire du rayon « ${this.rayon().name} » : ${written.length} correction(s) enregistrée(s).`);
          this.changed.emit();
          this.closed.emit();
        },
        // Nothing was written: the error message says which product, the count stays as typed.
        error: () => {
          this.busy.set(false);
          this.reviewing.set(false);
        },
      });
  }

  protected onEscape(): void {
    if (isTopmostDialog(this.host.nativeElement)) {
      this.closed.emit();
    }
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
