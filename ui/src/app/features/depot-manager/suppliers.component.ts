import { Component, inject } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { SUPPLIER_IMPORT, Supplier } from '../../core/models';
import { SimpleImportComponent } from '../../shared/components/simple-import.component';

@Component({
  selector: 'app-suppliers',
  standalone: true,
  imports: [SimpleImportComponent],
  template: `
    <div class="card">
      <div class="head-row">
        <h2>
          Fournisseurs
          <small>contacts du dépôt et volumes reçus</small>
        </h2>
        <app-simple-import [kind]="importKind" (imported)="resource.reload()" />
      </div>
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
        <div class="empty">
          Aucun fournisseur enregistré. Importez votre carnet de contacts : sans fournisseur, le
          dépôt ne peut enregistrer aucun approvisionnement.
        </div>
      }
    </div>
  `,
})
export class SuppliersComponent {
  private readonly api = inject(CatalogApi);

  protected readonly importKind = SUPPLIER_IMPORT;

  // Delivery counts are aggregated server-side, in the same query as the suppliers.
  protected readonly resource = apiResource<Supplier[]>([], () => this.api.suppliers());
  protected readonly suppliers = this.resource.value;
}
