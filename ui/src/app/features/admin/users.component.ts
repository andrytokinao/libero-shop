import { Component, computed, inject, signal } from '@angular/core';
import { apiResource } from '../../core/api/api-resource';
import { UserApi } from '../../core/api/user.api';
import {
  CreateUserRequest,
  ROLE_LABELS,
  ROLE_PRECEDENCE,
  RoleApp,
  SetPasswordRequest,
  UpdateUserRequest,
  UserActivity,
  UserApp,
  describeRoles,
} from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { AriaryPipe } from '../../shared/pipes/ariary.pipe';
import { UserDialogComponent } from './user-dialog.component';
import { UserPasswordDialogComponent } from './user-password-dialog.component';

/**
 * Account administration: who works here, what each of them may do, and what they did today.
 *
 * <p>The figures sit next to the actions on purpose. Whether an account should be switched
 * off is a question about what it has been doing — an agent still holding cash from this
 * morning is not one to revoke before the hand-over — so the day's activity is read on the
 * same line as the button.
 *
 * <p>No row can be deleted, because every sale, payment and hand-over is signed by an
 * account and the trail has to keep naming someone. A departure is a disabled account, and
 * it is reversible in one click, which is why the button asks for no confirmation.
 *
 * <p>Two refusals are mirrored here as disabled buttons rather than waited for: switching
 * your own account off, and switching off the last super-admin who can still sign in. The
 * server refuses both anyway; showing why up front beats a toast after the click.
 */
@Component({
  selector: 'app-users',
  standalone: true,
  imports: [AriaryPipe, UserDialogComponent, UserPasswordDialogComponent],
  template: `
    <div class="card">
      <div class="head-row">
        <h2>
          Utilisateurs
          <small>activité du jour, rôles et état de chaque compte</small>
        </h2>
        <button class="btn small" type="button" (click)="openCreate()">＋ Nouveau compte</button>
      </div>

      @if (activity().length) {
        <div class="table-scroll">
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
                <th class="col-actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (row of activity(); track row.user.id) {
                <tr [class.off]="!row.user.enabled">
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
                  <td class="col-actions">
                    <div class="actions">
                      <button
                        class="btn small ghost"
                        type="button"
                        [disabled]="busyId() === row.user.id"
                        (click)="openEdit(row.user)"
                      >
                        Modifier
                      </button>
                      <button
                        class="btn small ghost"
                        type="button"
                        [disabled]="busyId() === row.user.id"
                        (click)="passwordFor.set(row.user)"
                      >
                        Mot de passe
                      </button>
                      @if (row.user.enabled) {
                        <button
                          class="btn small ghost danger"
                          type="button"
                          [disabled]="busyId() === row.user.id || !!blockedReason(row.user)"
                          [title]="blockedReason(row.user) ?? 'Coupe l’accès sans rien effacer'"
                          (click)="setEnabled(row.user, false)"
                        >
                          Désactiver
                        </button>
                      } @else {
                        <button
                          class="btn small ghost"
                          type="button"
                          [disabled]="busyId() === row.user.id"
                          (click)="setEnabled(row.user, true)"
                        >
                          Réactiver
                        </button>
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
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
            <th class="num">Comptes actifs</th>
            <th>Utilisateurs</th>
          </tr>
        </thead>
        <tbody>
          @for (role of roles; track role) {
            <tr>
              <td>{{ roleLabels[role] }}</td>
              <td class="num">{{ enabledCountOf(role) }}</td>
              <td class="muted">{{ namesOf(role) || '—' }}</td>
            </tr>
          }
        </tbody>
      </table>
      @if (uncoveredRoles().length) {
        <div class="gap-warn">
          Plus aucun compte actif pour :
          {{ uncoveredRoles().join(', ') }}. Ces écrans n'ont plus personne pour les tenir.
        </div>
      }
    </div>

    @if (formOpen()) {
      <app-user-dialog
        [user]="editing()"
        [busy]="saving()"
        (create)="create($event)"
        (update)="update($event)"
        (closed)="closeForm()"
      />
    }

    @if (passwordFor(); as target) {
      <app-user-password-dialog
        [user]="target"
        [busy]="saving()"
        (submitted)="setPassword(target, $event)"
        (closed)="passwordFor.set(null)"
      />
    }
  `,
  styles: `
    .head-row {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 14px;

      h2 {
        margin: 0;
      }
    }

    /* Nine columns do not fit a laptop once the buttons are there. */
    .table-scroll {
      overflow-x: auto;
    }

    .col-actions {
      width: 1%;
      white-space: nowrap;
    }

    .actions {
      display: flex;
      gap: 6px;
    }

    /* A revoked account stays in the list, and has to read as retired at a glance. */
    tr.off td {
      opacity: 0.6;
    }

    .btn.danger {
      color: var(--red);
      border-color: var(--red-soft);

      &:hover:not(:disabled) {
        background: var(--red-soft);
      }
    }

    .gap-warn {
      margin-top: 12px;
      padding: 9px 11px;
      border-radius: 7px;
      background: var(--amber-soft);
      color: var(--amber);
      font-size: 12.5px;
    }
  `,
})
export class UsersComponent {
  private readonly api = inject(UserApi);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);

  protected readonly roleLabels = ROLE_LABELS;
  protected readonly roles = ROLE_PRECEDENCE;
  protected readonly describe = describeRoles;

  private readonly resource = apiResource<UserActivity[]>([], () => this.api.activity());
  protected readonly activity = this.resource.value;

  /** Which row has a call in flight, so only its own buttons go quiet. */
  protected readonly busyId = signal<number | null>(null);
  protected readonly saving = signal(false);
  protected readonly formOpen = signal(false);
  /** The account being corrected; null while the dialog is opening a new one. */
  protected readonly editing = signal<UserApp | null>(null);
  protected readonly passwordFor = signal<UserApp | null>(null);

  private readonly users = computed<UserApp[]>(() => this.activity().map((row) => row.user));

  /** Roles no enabled account holds any more — an operational hole, not a cosmetic one. */
  protected readonly uncoveredRoles = computed(() =>
    ROLE_PRECEDENCE.filter((role) => this.enabledCountOf(role) === 0).map(
      (role) => ROLE_LABELS[role],
    ),
  );

  protected enabledCountOf(role: RoleApp): number {
    return this.users().filter((user) => user.enabled && user.roles.includes(role)).length;
  }

  /** Disabled accounts are named too, marked, so a gap in a role is visible with its cause. */
  protected namesOf(role: RoleApp): string {
    return this.users()
      .filter((user) => user.roles.includes(role))
      .map((user) => (user.enabled ? user.fullName : `${user.fullName} (désactivé)`))
      .join(', ');
  }

  /**
   * Why this account cannot be switched off, or null when it can.
   *
   * <p>Only your own account, which is the one refusal this screen can actually run into.
   * The server also refuses switching off the last super-admin able to sign in — but whoever
   * is reading this page is one, so any *other* admin row means there are two, and that
   * refusal cannot be reached from here.
   */
  protected blockedReason(user: UserApp): string | null {
    return user.id === this.auth.currentUser()?.id
      ? 'Vous ne pouvez pas désactiver votre propre compte.'
      : null;
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.formOpen.set(true);
  }

  protected openEdit(user: UserApp): void {
    this.editing.set(user);
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    this.formOpen.set(false);
    this.editing.set(null);
  }

  protected create(request: CreateUserRequest): void {
    this.saving.set(true);
    this.api.create(request).subscribe({
      next: (user) => {
        this.done(`Compte ${user.username} créé pour ${user.fullName}.`);
        this.closeForm();
      },
      error: () => this.saving.set(false),
    });
  }

  protected update(request: UpdateUserRequest): void {
    const target = this.editing();
    if (!target) {
      return;
    }
    const rolesChanged = describeRoles(target.roles) !== describeRoles(request.roles);
    this.saving.set(true);
    this.api.update(target.id, request).subscribe({
      next: (user) => {
        // Said plainly rather than left to be discovered: the rights a token carries are
        // fixed when it is minted, so a role change only lands at the next sign-in.
        this.done(
          rolesChanged
            ? `${user.fullName} : ${describeRoles(user.roles)}. Le changement prendra effet ` +
              'à sa prochaine connexion.'
            : `${user.fullName} renommé.`,
        );
        this.closeForm();
      },
      error: () => this.saving.set(false),
    });
  }

  protected setPassword(target: UserApp, request: SetPasswordRequest): void {
    this.saving.set(true);
    this.api.setPassword(target.id, request).subscribe({
      next: (user) => {
        this.done(`Nouveau mot de passe défini pour ${user.fullName} (${user.username}).`);
        this.passwordFor.set(null);
      },
      error: () => this.saving.set(false),
    });
  }

  protected setEnabled(user: UserApp, enabled: boolean): void {
    this.busyId.set(user.id);
    this.api.setEnabled(user.id, enabled).subscribe({
      next: (updated) => {
        this.busyId.set(null);
        this.resource.reload();
        // Precise on purpose: a token already issued still opens read-only screens until
        // it expires, so promising "plus aucun accès" would be a promise the server keeps
        // only for what the account tries to record.
        this.toasts.show(
          enabled
            ? `${updated.fullName} peut se reconnecter.`
            : `${updated.fullName} ne peut plus se connecter ni rien enregistrer. Son ` +
              'historique est conservé et le compte peut être réactivé à tout moment.',
        );
      },
      error: () => this.busyId.set(null),
    });
  }

  /**
   * The list is re-fetched rather than patched with the answer: the activity figures beside
   * each row come from the same call, and a new account has to appear in the right place in
   * the alphabetical order the server decides.
   */
  private done(message: string): void {
    this.saving.set(false);
    this.resource.reload();
    this.toasts.show(message);
  }
}
