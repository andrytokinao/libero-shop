import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { StockApi } from '../../core/api/stock.api';
import { ADJUSTMENT_REASONS, Product, ProductMovement } from '../../core/models';
import { formatAmount } from '../../core/sale/sale-unit';
import { InvoiceLinkComponent } from './invoice-link.component';
import { AriaryPipe } from '../pipes/ariary.pipe';

/**
 * Everything that moved one product's stock — receipts, sales, corrections — newest first.
 *
 * <p>Read a page at a time: a product that sells every day has a line per sale, and the last few
 * are what is looked for. Nothing is edited here; a wrong entry is put right by a correction,
 * which is itself a line of this list.
 */
@Component({
  selector: 'app-stock-history-dialog',
  standalone: true,
  imports: [DatePipe, AriaryPipe, InvoiceLinkComponent],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal wide" role="dialog" aria-modal="true" aria-labelledby="sh-title">
        <div class="modal-head">
          <div>
            <h2 id="sh-title">Historique du stock — {{ product().name }}</h2>
            <div class="sub muted">
              En stock : {{ formatAmount(product().stockQuantity, product().unit) }}
              @if (total() !== null) {
                · {{ total() }} mouvement(s)
              }
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          @if (movements().length) {
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Mouvement</th>
                    <th class="num">Quantité</th>
                    <th>Détail</th>
                    <th>Par</th>
                  </tr>
                </thead>
                <tbody>
                  @for (movement of movements(); track movement.id) {
                    <tr>
                      <td class="muted">{{ movement.movementDate | date: 'dd/MM/yy HH:mm' }}</td>
                      <td>{{ kind(movement) }}</td>
                      <td class="num" [class.sh-out]="movement.quantity < 0">{{ signed(movement) }}</td>
                      <td class="muted">
                        @switch (movement.type) {
                          @case ('SUPPLY') {
                            {{ movement.supplierName ?? 'Sans fournisseur' }}
                            @if (movement.unitCost !== null) {
                              · {{ movement.unitCost | ariary }} / {{ product().unit ?? 'unité' }}
                            }
                          }
                          @case ('OUTPUT') {
                            Facture
                            @if (movement.invoiceId !== null && movement.invoiceNumber !== null) {
                              <app-invoice-link [invoiceId]="movement.invoiceId" [number]="movement.invoiceNumber" />
                            }
                          }
                          @default {
                            {{ movement.comment ?? '—' }}
                          }
                        }
                      </td>
                      <td>{{ movement.performedBy }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (hasMore()) {
              <div style="text-align:center; margin-top:12px;">
                <button class="btn small ghost" type="button" [disabled]="loading()" (click)="load()">
                  {{ loading() ? 'Chargement...' : 'Afficher plus' }}
                </button>
              </div>
            }
          } @else {
            <div class="empty">
              {{ loading() ? 'Chargement…' : 'Aucun mouvement de stock pour ce produit.' }}
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
    .sh-out {
      color: var(--red);
    }
  `,
})
export class StockHistoryDialogComponent implements OnInit {
  private readonly api = inject(StockApi);

  readonly product = input.required<Product>();
  readonly closed = output<void>();

  protected readonly movements = signal<ProductMovement[]>([]);
  protected readonly total = signal<number | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(false);
  private nextPage = 0;

  protected readonly formatAmount = formatAmount;

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    if (this.loading()) {
      return;
    }
    this.loading.set(true);
    this.api.productMovements(this.product().id, this.nextPage).subscribe({
      next: (page) => {
        this.movements.update((known) => [...known, ...page.items]);
        this.total.set(page.total);
        this.hasMore.set(page.hasMore);
        this.nextPage = page.page + 1;
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected kind(movement: ProductMovement): string {
    switch (movement.type) {
      case 'SUPPLY':
        return 'Entrée';
      case 'OUTPUT':
        return 'Vente';
      case 'ADJUSTMENT':
        return (
          ADJUSTMENT_REASONS.find((choice) => choice.value === movement.reason)?.label ?? 'Correction'
        );
    }
  }

  protected signed(movement: ProductMovement): string {
    const sign = movement.quantity > 0 ? '+' : '−';
    return `${sign}${formatAmount(Math.abs(movement.quantity), this.product().unit)}`;
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
