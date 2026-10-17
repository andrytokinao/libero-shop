import { Component, inject, signal } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { SUPPLIER_IMPORT, Supplier } from '../../core/models';
import { SimpleImportComponent } from '../../shared/components/simple-import.component';
import { SupplierCreateDialogComponent } from '../../shared/components/supplier-create-dialog.component';

@Component({
  selector: 'app-suppliers',
  standalone: true,
  imports: [SimpleImportComponent, SupplierCreateDialogComponent],
  template: `
    <div class="card">
      <div class="head-row">
        <h2>
          Fournisseurs
          <small>contacts du dépôt et volumes reçus</small>
        </h2>
        <div class="sp-actions">
          <button class="btn small" type="button" (click)="creating.set(true)">＋ Nouveau fournisseur</button>
          <app-simple-import [kind]="importKind" (imported)="resource.reload()" />
        </div>
      </div>
      @if (suppliers().length) {
        <div class="table-scroll">
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
        </div>
      } @else if (!resource.loading()) {
        <!-- Without a supplier a receipt is booked "Sans fournisseur": the price comparison is lost. -->
        <div class="empty">
          Aucun fournisseur pour l'instant.
          <div class="empty-actions">
            <button class="btn small" type="button" (click)="creating.set(true)">＋ Nouveau fournisseur</button>
            <app-simple-import [kind]="importKind" (imported)="resource.reload()" />
          </div>
        </div>
      }
    </div>

    @if (creating()) {
      <app-supplier-create-dialog
        hint="Le nom suffit ; le téléphone est facultatif."
        (created)="resource.reload()"
        (closed)="creating.set(false)"
      />
    }
  `,
  styles: `
    .sp-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  `,
})
export class SuppliersComponent {
  private readonly api = inject(CatalogApi);

  protected readonly importKind = SUPPLIER_IMPORT;
  protected readonly creating = signal(false);

  // Delivery counts are aggregated server-side, in the same query as the suppliers.
  protected readonly resource = apiResource<Supplier[]>([], () => this.api.suppliers());
  protected readonly suppliers = this.resource.value;
}
