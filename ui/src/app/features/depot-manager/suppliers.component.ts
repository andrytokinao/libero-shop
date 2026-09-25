import { Component, inject } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { Supplier } from '../../core/models';

@Component({
  selector: 'app-suppliers',
  standalone: true,
  template: `
    <div class="card">
      <h2>
        Fournisseurs
        <small>contacts du dépôt et volumes reçus</small>
      </h2>
      @if (suppliers().length) {
        <table>
          <thead>
            <tr>
              <th>Nom</th>
              <th>Contact</th>
              <th>Produits fournis</th>
              <th class="num">Livraisons</th>
              <th class="num">Unités reçues</th>
            </tr>
          </thead>
          <tbody>
            @for (supplier of suppliers(); track supplier.id) {
              <tr>
                <td>{{ supplier.name }}</td>
                <td>{{ supplier.contact ?? '—' }}</td>
                <td class="muted">{{ supplier.suppliedProducts ?? '—' }}</td>
                <td class="num">{{ supplier.deliveryCount }}</td>
                <td class="num">{{ supplier.unitsReceived }}</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty">Aucun fournisseur enregistré.</div>
      }
    </div>
  `,
})
export class SuppliersComponent {
  private readonly api = inject(CatalogApi);
  // Delivery counts are aggregated server-side, in the same query as the suppliers.
  private readonly resource = apiResource<Supplier[]>([], () => this.api.suppliers());
  protected readonly suppliers = this.resource.value;
}
