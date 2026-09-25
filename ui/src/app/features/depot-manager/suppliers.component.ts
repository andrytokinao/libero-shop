import { Component, computed, inject } from '@angular/core';
import { Supplier } from '../../core/models';
import { ShopStore } from '../../core/services/shop-store.service';

@Component({
  selector: 'app-suppliers',
  standalone: true,
  template: `
    <div class="card">
      <h2>
        Fournisseurs
        <small>contacts du dépôt et volumes reçus</small>
      </h2>
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
          @for (supplier of store.suppliers(); track supplier.id) {
            <tr>
              <td>{{ supplier.name }}</td>
              <td>{{ supplier.contact ?? '—' }}</td>
              <td class="muted">{{ supplier.suppliedProducts ?? '—' }}</td>
              <td class="num">{{ deliveryCount(supplier) }}</td>
              <td class="num">{{ unitCount(supplier) }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class SuppliersComponent {
  protected readonly store = inject(ShopStore);

  private readonly suppliesBySupplier = computed(() => {
    const grouped = new Map<number, { deliveries: number; units: number }>();
    for (const supply of this.store.supplies()) {
      const current = grouped.get(supply.supplier.id) ?? { deliveries: 0, units: 0 };
      grouped.set(supply.supplier.id, {
        deliveries: current.deliveries + 1,
        units: current.units + supply.quantity,
      });
    }
    return grouped;
  });

  protected deliveryCount(supplier: Supplier): number {
    return this.suppliesBySupplier().get(supplier.id)?.deliveries ?? 0;
  }

  protected unitCount(supplier: Supplier): number {
    return this.suppliesBySupplier().get(supplier.id)?.units ?? 0;
  }
}
