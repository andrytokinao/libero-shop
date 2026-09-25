import { Component, computed, inject } from '@angular/core';
import { ROLE_LABELS, RoleApp, UserApp } from '../../core/models';
import { ShopStore } from '../../core/services/shop-store.service';
import { isToday } from '../../core/utils/date.util';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

interface UserActivity {
  user: UserApp;
  salesToday: number;
  revenueToday: number;
  deliveries: number;
  remittances: number;
}

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [AriaryPipe],
  template: `
    <div class="card">
      <h2>
        Utilisateurs
        <small>activité du jour par compte</small>
      </h2>
      <table>
        <thead>
          <tr>
            <th>Nom</th>
            <th>Rôle</th>
            <th class="num">Ventes du jour</th>
            <th class="num">CA encaissé</th>
            <th class="num">Remises</th>
            <th class="num">Versements</th>
          </tr>
        </thead>
        <tbody>
          @for (row of activity(); track row.user.id) {
            <tr>
              <td>{{ row.user.fullName }}</td>
              <td class="muted">{{ roleLabels[row.user.role] }}</td>
              <td class="num">{{ row.user.role === RoleApp.CASHIER ? row.salesToday : '—' }}</td>
              <td class="num">
                {{ row.revenueToday ? (row.revenueToday | ariary) : '—' }}
              </td>
              <td class="num">{{ row.deliveries || '—' }}</td>
              <td class="num">{{ row.remittances || '—' }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>Répartition des rôles</h2>
      <table>
        <thead>
          <tr>
            <th>Rôle</th>
            <th class="num">Comptes</th>
            <th>Utilisateurs</th>
          </tr>
        </thead>
        <tbody>
          @for (role of roles; track role) {
            <tr>
              <td>{{ roleLabels[role] }}</td>
              <td class="num">{{ usersOf(role).length }}</td>
              <td class="muted">{{ namesOf(role) }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class UsersComponent {
  private readonly store = inject(ShopStore);

  protected readonly roleLabels = ROLE_LABELS;
  protected readonly RoleApp = RoleApp;
  protected readonly roles = Object.values(RoleApp);

  protected readonly activity = computed<UserActivity[]>(() =>
    this.store.users().map((user) => {
      const salesToday = this.store
        .salesOf(user)
        .filter((sale) => isToday(sale.saleDate));
      const paymentsToday = this.store
        .payments()
        .filter((p) => p.collectedBy.id === user.id && isToday(p.paymentDate));

      return {
        user,
        salesToday: salesToday.length,
        revenueToday: paymentsToday.reduce((total, p) => total + p.amount, 0),
        deliveries: paymentsToday.filter((p) => p.collectedBy.role === RoleApp.DEPOT_AGENT).length,
        remittances: this.store.remittancesOf(user).length,
      };
    }),
  );

  protected usersOf(role: RoleApp): UserApp[] {
    return this.store.users().filter((u) => u.role === role);
  }

  protected namesOf(role: RoleApp): string {
    return this.usersOf(role)
      .map((u) => u.fullName)
      .join(', ');
  }
}
