import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ShopStore } from '../../core/services/shop-store.service';
import { isToday } from '../../core/utils/date.util';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
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
  ],
  template: `
    <div class="grid g3">
      <app-kpi-card label="Sorties du jour" [value]="outputsToday().length" />
      <app-kpi-card label="Unités sorties aujourd'hui" [value]="unitsToday()" />
      <app-kpi-card label="Valeur sortie aujourd'hui" [value]="valueToday() | ariary" />
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
            (ngModelChange)="search.set($event)"
          />
        </div>
      </div>
      @if (filtered().length) {
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
            @for (output of filtered(); track output.id) {
              <tr>
                <td class="muted">{{ output.movementDate | date: 'dd/MM HH:mm' }}</td>
                <td>{{ output.product.name }}</td>
                <td class="num">−{{ output.quantity }}</td>
                <td>{{ output.invoice.invoiceNumber }}</td>
                <td>{{ output.invoice.clientName }}</td>
                <td class="num">{{ output.product.price * output.quantity | ariary }}</td>
                <td><app-delivery-status-badge [status]="output.invoice.deliveryStatus" /></td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty">Aucune sortie ne correspond à la recherche.</div>
      }
    </div>
  `,
})
export class StockOutputsComponent {
  private readonly store = inject(ShopStore);

  protected readonly search = signal('');

  protected readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const outputs = this.store.stockOutputs();
    if (!term) {
      return outputs;
    }
    return outputs.filter((o) =>
      [o.product.name, o.invoice.invoiceNumber, o.invoice.clientName].some((field) =>
        field.toLowerCase().includes(term),
      ),
    );
  });

  protected readonly outputsToday = computed(() =>
    this.store.stockOutputs().filter((o) => isToday(o.movementDate)),
  );

  protected readonly unitsToday = computed(() =>
    this.outputsToday().reduce((total, o) => total + o.quantity, 0),
  );

  protected readonly valueToday = computed(() =>
    this.outputsToday().reduce((total, o) => total + o.product.price * o.quantity, 0),
  );
}
