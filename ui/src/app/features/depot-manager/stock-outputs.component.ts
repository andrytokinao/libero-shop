import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StockApi } from '../../core/api/stock.api';
import { StockOutput, StockOutputPage } from '../../core/models';
import { reloadOnOrderChange } from '../../core/realtime/reload-on';
import { formatQuantity } from '../../core/sale/sale-unit';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { InvoiceLinkComponent } from '../../shared/components/invoice-link.component';
import { DeliveryStatusBadgeComponent } from '../../shared/components/status-badges.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-stock-outputs',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    KpiCardComponent,
    DeliveryStatusBadgeComponent,
    AriaryPipe,
    InvoiceLinkComponent,
  ],
  template: `
    <div class="grid g3">
      <!-- The whole answer's figures, from the server: the table below only holds a page of it. -->
      <app-kpi-card label="Sorties trouvées" [value]="total()" />
      <app-kpi-card label="Unités sorties" [value]="formatQuantity(units())" />
      <app-kpi-card label="Valeur sortie" [value]="value() | ariary" />
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Sorties de dépôt
        <small>un mouvement OUTPUT par ligne de commande vendue</small>
      </h2>
      <div class="form-row">
        <div class="fld" style="flex:1; max-width:320px;">
          <label for="output-search">Recherche</label>
          <input
            id="output-search"
            type="text"
            placeholder="Produit, facture ou client"
            [ngModel]="search()"
            (ngModelChange)="onSearch($event)"
          />
        </div>
      </div>
      @if (outputs().length) {
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Produit</th>
                <th class="num">Quantité</th>
                <th>Facture</th>
                <th>Client</th>
                <th class="num">Valeur</th>
                <th>Remise</th>
              </tr>
            </thead>
            <tbody>
              @for (output of outputs(); track output.id) {
                <tr>
                  <td class="muted">{{ output.movementDate | date: 'dd/MM HH:mm' }}</td>
                  <td>{{ output.product.name }}</td>
                  <td class="num">−{{ output.quantity }}</td>
                  <td><app-invoice-link [invoiceId]="output.invoice.id" [number]="output.invoice.invoiceNumber" /></td>
                  <td>{{ output.invoice.clientName }}</td>
                  <td class="num">{{ output.value | ariary }}</td>
                  <td><app-delivery-status-badge [status]="output.invoice.deliveryStatus" /></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="more-row">
          <span class="muted">{{ outputs().length }} sur {{ total() }}</span>
          @if (hasMore()) {
            <button class="btn small ghost" type="button" [disabled]="loading()" (click)="showMore()">
              {{ loading() ? 'Chargement…' : 'Afficher plus' }}
            </button>
          }
        </div>
      } @else if (loading()) {
        <div class="empty">Chargement…</div>
      } @else {
        <div class="empty">Aucune sortie ne correspond à la recherche.</div>
      }
    </div>
  `,
  styles: `
    .more-row {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      margin-top: 12px;
      font-size: 12.5px;
    }
  `,
})
export class StockOutputsComponent {
  private readonly api = inject(StockApi);

  protected readonly search = signal('');
  /** The rows shown: the pages fetched so far, one after the other. */
  protected readonly outputs = signal<StockOutput[]>([]);
  /** The last page received: its totals are the whole answer's, whatever page it is. */
  private readonly lastPage = signal<StockOutputPage | null>(null);
  protected readonly loading = signal(false);

  protected readonly total = computed(() => this.lastPage()?.total ?? 0);
  protected readonly units = computed(() => this.lastPage()?.totalQuantity ?? 0);
  protected readonly value = computed(() => this.lastPage()?.totalValue ?? 0);
  protected readonly hasMore = computed(() => this.lastPage()?.hasMore ?? false);

  protected readonly formatQuantity = formatQuantity;

  /** Counts the requests sent, so an answer overtaken by a newer search is dropped. */
  private requests = 0;

  constructor() {
    this.load(0);
    // Each output shows its order's hand-over status, which the depot changes — and a sale
    // adds rows at the top. Only while the first page alone is shown: reloading under someone
    // reading further down would throw them back to the top.
    reloadOnOrderChange({
      reload: () => {
        if ((this.lastPage()?.page ?? 0) === 0) {
          this.load(0);
        }
      },
    });
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    this.load(0);
  }

  protected showMore(): void {
    const last = this.lastPage();
    if (last?.hasMore && !this.loading()) {
      this.load(last.page + 1);
    }
  }

  /** Page 0 replaces the rows; any other page is added under them. */
  private load(page: number): void {
    const request = ++this.requests;
    this.loading.set(true);
    this.api.outputs(this.search(), page).subscribe({
      next: (found) => {
        if (request !== this.requests) {
          return;
        }
        this.outputs.update((shown) => (page === 0 ? found.items : [...shown, ...found.items]));
        this.lastPage.set(found);
        this.loading.set(false);
      },
      // The interceptor has said why; what is shown stays as it was.
      error: () => {
        if (request === this.requests) {
          this.loading.set(false);
        }
      },
    });
  }
}
