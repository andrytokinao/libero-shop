import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { StockApi } from '../../core/api/stock.api';
import { StockOutput } from '../../core/models';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';
import { DeliveryStatusBadgeComponent } from '../../shared/components/status-badges.component';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

@Component({
  selector: 'app-stock-outputs',
  standalone: true,
  imports: [FormsModule, DatePipe, KpiCardComponent, DeliveryStatusBadgeComponent, AriaryPipe],
  template: `
    <div class="grid g3">
      <app-kpi-card label="Sorties affichées" [value]="outputs().length" />
      <app-kpi-card label="Unités sorties" [value]="units()" />
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
                <td>{{ output.invoice.invoiceNumber }}</td>
                <td>{{ output.invoice.clientName }}</td>
                <td class="num">{{ output.value | ariary }}</td>
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
  private readonly api = inject(StockApi);

  protected readonly search = signal('');

  private readonly resource = apiResource<StockOutput[]>([], () => this.api.outputs(this.search()));
  protected readonly outputs = this.resource.value;

  protected readonly units = computed(() =>
    this.outputs().reduce((total, output) => total + output.quantity, 0),
  );

  protected readonly value = computed(() =>
    this.outputs().reduce((total, output) => total + output.value, 0),
  );

  protected onSearch(value: string): void {
    this.search.set(value);
    this.resource.reload();
  }
}
