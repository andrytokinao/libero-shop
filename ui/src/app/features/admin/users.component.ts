import { Component, computed, inject } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { DashboardApi } from '../../core/api/dashboard.api';
import { ROLE_LABELS, RoleApp, UserActivity, UserApp, describeRoles } from '../../core/models';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';

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
      @if (activity().length) {
        <table>
          <thead>
            <tr>
              <th>Nom</th>
              <th>Identifiant</th>
              <th>Rôle</th>
              <th>Compte</th>
              <th class="num">Ventes du jour</th>
              <th class="num">Encaissé</th>
              <th class="num">Remises</th>
              <th class="num">Versements</th>
            </tr>
          </thead>
          <tbody>
            @for (row of activity(); track row.user.id) {
              <tr>
                <td>{{ row.user.fullName }}</td>
                <td class="muted">{{ row.user.username }}</td>
                <td class="muted">{{ describe(row.user.roles) }}</td>
                <td>
                  @if (row.user.enabled) {
                    <span class="badge green">Actif</span>
                  } @else {
                    <span class="badge red">Désactivé</span>
                  }
                </td>
                <td class="num">{{ row.salesToday || '—' }}</td>
                <td class="num">{{ row.collectedToday ? (row.collectedToday | ariary) : '—' }}</td>
                <td class="num">{{ row.deliveriesToday || '—' }}</td>
                <td class="num">{{ row.remittances || '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty">Aucun utilisateur enregistré.</div>
      }
    </div>

    <div class="card" style="margin-top:16px;">
      <h2>
        Répartition des rôles
        <small>un compte peut en cumuler plusieurs et apparaître sur plusieurs lignes</small>
      </h2>
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
              <td class="muted">{{ namesOf(role) || '—' }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class UsersComponent {
  private readonly api = inject(DashboardApi);

  protected readonly roleLabels = ROLE_LABELS;
  protected readonly roles = Object.values(RoleApp);
  protected readonly describe = describeRoles;

  private readonly resource = apiResource<UserActivity[]>([], () => this.api.userActivity());
  protected readonly activity = this.resource.value;

  private readonly users = computed<UserApp[]>(() => this.activity().map((row) => row.user));

  protected usersOf(role: RoleApp): UserApp[] {
    return this.users().filter((user) => user.roles.includes(role));
  }

  protected namesOf(role: RoleApp): string {
    return this.usersOf(role)
      .map((user) => user.fullName)
      .join(', ');
  }
}
