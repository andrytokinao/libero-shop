import { DatePipe } from '@angular/common';
import { Component, ElementRef, computed, inject, input, output } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { StockApi } from '../../core/api/stock.api';
import { Supplier, Supply } from '../../core/models';
import { SupplierPurchase, purchasesFrom } from '../../core/stock/supplier-purchases';
import { isTopmostDialog } from '../../core/ui/topmost-dialog';
import { AriaryPipe } from '../pipes/ariary.pipe';

/**
 * "Ses prix": what the shop has bought from one supplier, product by product — the last price
 * paid for each unit, when, and how many receipts — read from the receipts themselves.
 */
@Component({
  selector: 'app-supplier-purchases-dialog',
  standalone: true,
  imports: [DatePipe, AriaryPipe],
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal wide" role="dialog" aria-modal="true" aria-labelledby="sp-title">
        <div class="modal-head">
          <div>
            <h2 id="sp-title">Ce qu'on a acheté à {{ supplier().name }}</h2>
            <div class="sub muted">Le dernier prix payé par produit et par unité, d'après les réceptions.</div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>
        <div class="modal-body">
          @if (lines().length) {
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Produit</th>
                    <th>Unité</th>
                    <th class="num">Dernier prix</th>
                    <th>Le</th>
                    <th class="num">Réceptions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (line of lines(); track keyOf(line)) {
                    <tr>
                      <td>{{ line.productName }}</td>
                      <td class="muted">{{ line.unitLabel ?? line.baseUnit ?? 'unité' }}</td>
                      <td class="num">{{ line.lastCost === null ? '—' : (line.lastCost | ariary) }}</td>
                      <td class="muted">{{ line.lastDate | date: 'dd/MM/yyyy' }}</td>
                      <td class="num">{{ line.receipts }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else if (!resource.loading()) {
            <div class="empty">Aucune réception de ce fournisseur pour l'instant.</div>
          }
        </div>
        <div class="modal-foot">
          <button class="btn ghost" type="button" (click)="closed.emit()">Fermer</button>
        </div>
      </div>
    </div>
  `,
})
export class SupplierPurchasesDialogComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly stock = inject(StockApi);

  readonly supplier = input.required<Supplier>();
  readonly closed = output<void>();

  protected readonly resource = apiResource<Supply[]>([], () => this.stock.supplies());
  protected readonly lines = computed(() => purchasesFrom(this.resource.value(), this.supplier().id));

  /** One line per product and unit. A function: `??` inside a `track` trips the template compiler. */
  protected keyOf(line: SupplierPurchase): string {
    return `${line.productName}|${line.unitLabel ?? ''}`;
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
