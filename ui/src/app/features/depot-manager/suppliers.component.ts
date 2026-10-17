import { Component, inject, signal } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { CatalogApi } from '../../core/api/catalog.api';
import { ProductRights } from '../../core/catalog/product-rights';
import { SUPPLIER_IMPORT, Supplier } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { ActionMenuComponent, MenuAction } from '../../shared/components/action-menu.component';
import { DeliveryDialogComponent } from '../../shared/components/delivery-dialog.component';
import { SimpleImportComponent } from '../../shared/components/simple-import.component';
import { SupplierCreateDialogComponent } from '../../shared/components/supplier-create-dialog.component';
import { SupplierPurchasesDialogComponent } from '../../shared/components/supplier-purchases-dialog.component';
import { LoadStateComponent } from '../../shared/components/load-state.component';

/** What can be done to one supplier from its "⋮". */
type SupplierAction = 'edit' | 'prices' | 'receive' | 'retire';

@Component({
  selector: 'app-suppliers',
  standalone: true,
  imports: [
    LoadStateComponent,
    SimpleImportComponent,
    SupplierCreateDialogComponent,
    SupplierPurchasesDialogComponent,
    DeliveryDialogComponent,
    ActionMenuComponent,
  ],
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
      <app-load-state [of]="resource" what="les fournisseurs" />
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
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (supplier of suppliers(); track supplier.id) {
                <tr [class.sp-retired]="supplier.retired">
                  <td>
                    {{ supplier.name }}
                    @if (supplier.retired) {
                      <span class="badge grey" title="Plus proposé à la réception">Plus proposé</span>
                    }
                  </td>
                  <td>{{ supplier.contact ?? '—' }}</td>
                  <td class="muted">{{ supplier.suppliedProducts ?? '—' }}</td>
                  <td class="num">{{ supplier.deliveryCount }}</td>
                  <td class="num">{{ supplier.unitsReceived }}</td>
                  <td class="num">
                    <app-action-menu
                      [actions]="actionsFor(supplier)"
                      [label]="'Actions sur ' + supplier.name"
                      (picked)="pick(supplier, $event)"
                    />
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (resource.loaded()) {
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

    @if (editing(); as supplier) {
      <app-supplier-create-dialog
        hint="Ses réceptions passées gardent leur fournisseur."
        [editing]="supplier"
        (created)="resource.reload()"
        (closed)="editing.set(null)"
      />
    }

    @if (pricesOf(); as supplier) {
      <app-supplier-purchases-dialog [supplier]="supplier" (closed)="pricesOf.set(null)" />
    }

    @if (receivingFrom(); as supplier) {
      <app-delivery-dialog
        [initialSupplierId]="supplier.id"
        (changed)="resource.reload()"
        (closed)="receivingFrom.set(null)"
      />
    }
  `,
  styles: `
    .sp-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .sp-retired td {
      color: var(--ink-soft);
    }
  `,
})
export class SuppliersComponent {
  private readonly api = inject(CatalogApi);
  private readonly toasts = inject(ToastService);
  private readonly rights = inject(ProductRights);

  protected readonly importKind = SUPPLIER_IMPORT;
  protected readonly creating = signal(false);
  protected readonly editing = signal<Supplier | null>(null);
  protected readonly pricesOf = signal<Supplier | null>(null);
  protected readonly receivingFrom = signal<Supplier | null>(null);

  // Delivery counts are aggregated server-side, in the same query as the suppliers.
  protected readonly resource = apiResource<Supplier[]>([], () => this.api.suppliers());
  protected readonly suppliers = this.resource.value;

  /** A supplier no longer offered gets no new receipt, but can be offered again. */
  protected actionsFor(supplier: Supplier): MenuAction<SupplierAction>[] {
    return [
      { id: 'edit', label: 'Modifier' },
      { id: 'prices', label: 'Ses prix' },
      ...(this.rights.canAddStock() && !supplier.retired
        ? [{ id: 'receive' as const, label: 'Nouvelle réception' }]
        : []),
      supplier.retired
        ? { id: 'retire', label: 'Proposer à nouveau' }
        : { id: 'retire', label: 'Ne plus proposer', danger: true },
    ];
  }

  protected pick(supplier: Supplier, action: SupplierAction): void {
    switch (action) {
      case 'edit':
        this.editing.set(supplier);
        break;
      case 'prices':
        this.pricesOf.set(supplier);
        break;
      case 'receive':
        this.receivingFrom.set(supplier);
        break;
      case 'retire':
        this.setRetired(supplier, !supplier.retired);
        break;
    }
  }

  /** Reversible in one tap, and nothing is lost: no confirmation, the message says what changed. */
  private setRetired(supplier: Supplier, retired: boolean): void {
    this.api.changeSupplierRetired(supplier.id, retired).subscribe(() => {
      this.resource.reload();
      this.toasts.show(
        retired
          ? `« ${supplier.name} » n'est plus proposé à la réception ; ses prix restent.`
          : `« ${supplier.name} » est de nouveau proposé.`,
        [{ label: 'Annuler', run: () => this.setRetired(supplier, !retired) }],
      );
    });
  }
}
