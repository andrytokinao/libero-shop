import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { switchMap } from 'rxjs';
import { SetupApi, SetupStatus } from '../../core/api/setup.api';
import { ApiError } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';

/** As the server asks: see CreateFirstAdminRequest and InitialSetupService. */
const MIN_PASSWORD_LENGTH = 8;
const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,30}$/;

/**
 * The first start of an installation: nobody can sign in yet, so the first person at the
 * server's computer creates the administrator.
 *
 * <p>From any other device the page only explains where to go: the server refuses the creation
 * anywhere but on its own machine, so that a customer on the shop's Wi-Fi cannot get there first.
 * The password is typed twice — there is no one yet to reset it if it was mistyped.
 */
@Component({
  selector: 'app-first-admin',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="setup-page">
      <form class="card setup-card" (ngSubmit)="submit()">
        <div class="setup-brand">
          <img src="logo.png" alt="" />
          <div>
            <div class="mark">Libero <span>Shop</span></div>
            <div class="sub">Première utilisation</div>
          </div>
        </div>

        @if (status(); as s) {
          @if (s.allowedHere) {
            <p class="setup-lead">
              Cette installation n'a encore aucun compte. Créez celui de l'administrateur : il
              pourra ensuite ouvrir les comptes de la caisse et du dépôt.
            </p>

            <div class="fld">
              <label for="setup-name">Nom complet</label>
              <input
                id="setup-name"
                name="fullName"
                type="text"
                autocomplete="name"
                maxlength="80"
                autofocus
                [ngModel]="fullName()"
                (ngModelChange)="fullName.set($event)"
              />
            </div>
            <div class="fld">
              <label for="setup-username">Identifiant de connexion</label>
              <input
                id="setup-username"
                name="username"
                type="text"
                autocomplete="username"
                maxlength="30"
                aria-describedby="setup-username-hint"
                [ngModel]="username()"
                (ngModelChange)="username.set($event)"
              />
              <div id="setup-username-hint" class="hint muted">
                3 à 30 caractères : lettres, chiffres, point, tiret ou souligné. Il ne pourra plus
                être changé.
              </div>
            </div>
            <div class="fld">
              <label for="setup-password">Mot de passe</label>
              <input
                id="setup-password"
                name="password"
                type="password"
                autocomplete="new-password"
                maxlength="72"
                aria-describedby="setup-password-hint"
                [ngModel]="password()"
                (ngModelChange)="password.set($event)"
              />
              <div id="setup-password-hint" class="hint muted">
                Au moins {{ minPasswordLength }} caractères. Notez-le : personne d'autre ne peut
                encore le réinitialiser.
              </div>
            </div>
            <div class="fld">
              <label for="setup-confirm">Confirmer le mot de passe</label>
              <input
                id="setup-confirm"
                name="confirm"
                type="password"
                autocomplete="new-password"
                maxlength="72"
                [ngModel]="confirm()"
                (ngModelChange)="confirm.set($event)"
              />
            </div>

            @if (error() ?? problem(); as message) {
              <div class="setup-error" role="alert">{{ message }}</div>
            }

            <button class="btn block" type="submit" [disabled]="!canSubmit()">
              {{ submitting() ? 'Création...' : "Créer le compte administrateur" }}
            </button>
          } @else {
            <p class="setup-lead">
              Cette installation n'a encore aucun compte, et le premier se crée sur l'ordinateur
              où Libero Shop est installé.
            </p>
            <p class="setup-lead">
              Ouvrez Libero Shop sur cet ordinateur, créez le compte administrateur, puis revenez
              ici pour vous connecter.
            </p>
            <button class="btn block" type="button" (click)="check()">
              C'est fait, me connecter
            </button>
          }
        } @else {
          <div class="empty">Chargement…</div>
        }
      </form>
    </div>
  `,
  styles: `
    .setup-page {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--chrome-bg);
      padding: 24px;
    }

    .setup-card {
      width: 100%;
      max-width: 400px;
    }

    .setup-brand {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;

      img {
        width: 44px;
        height: 44px;
        border-radius: 10px;
      }

      .mark {
        font-size: 20px;
        font-weight: 700;
        color: var(--brand-dark);
      }

      .mark span {
        color: var(--brand);
      }

      .sub {
        font-size: 12.5px;
        color: var(--ink-soft);
      }
    }

    .setup-lead {
      font-size: 13.5px;
      line-height: 1.55;
      margin: 0 0 14px;
    }

    .fld input {
      width: 100%;
    }

    .hint {
      font-size: 11.5px;
      margin-top: 4px;
    }

    .setup-error {
      background: var(--red-soft);
      color: var(--red);
      border-radius: 7px;
      padding: 9px 11px;
      font-size: 12.5px;
      margin-bottom: 12px;
    }
  `,
})
export class FirstAdminComponent implements OnInit {
  private readonly api = inject(SetupApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly minPasswordLength = MIN_PASSWORD_LENGTH;

  protected readonly status = signal<SetupStatus | null>(null);
  protected readonly fullName = signal('');
  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly confirm = signal('');
  protected readonly submitting = signal(false);
  /** What the server answered; cleared as soon as the form is sent again. */
  protected readonly error = signal<string | null>(null);

  /**
   * What is wrong with the form as typed, said only once the field concerned has been filled:
   * an empty form is not a mistake yet.
   */
  protected readonly problem = computed<string | null>(() => {
    const username = this.username().trim();
    if (username && !USERNAME_PATTERN.test(username)) {
      return "L'identifiant accepte 3 à 30 lettres, chiffres, points, tirets ou soulignés, sans espace.";
    }
    const password = this.password();
    if (password && password.length < MIN_PASSWORD_LENGTH) {
      return `Le mot de passe doit faire au moins ${MIN_PASSWORD_LENGTH} caractères.`;
    }
    if (this.confirm() && this.confirm() !== password) {
      return 'Les deux mots de passe ne sont pas identiques.';
    }
    return null;
  });

  ngOnInit(): void {
    this.check();
  }

  /** Asks again: the account may have been created at the server meanwhile. */
  protected check(): void {
    this.api.status().subscribe((status) => {
      if (status.required) {
        this.status.set(status);
      } else {
        void this.router.navigateByUrl('/login');
      }
    });
  }

  protected canSubmit(): boolean {
    return (
      !this.submitting() &&
      this.problem() === null &&
      this.fullName().trim().length > 0 &&
      this.username().trim().length > 0 &&
      this.password().length > 0 &&
      this.confirm() === this.password()
    );
  }

  protected submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    const credentials = { username: this.username().trim(), password: this.password() };
    this.submitting.set(true);
    this.error.set(null);

    this.api
      .createFirstAdmin({ fullName: this.fullName().trim(), ...credentials })
      // Signed in straight away: typing the same two fields again on the next page would only
      // be a chance to mistype them.
      .pipe(switchMap(() => this.auth.login(credentials)))
      .subscribe({
        next: (session) => {
          this.submitting.set(false);
          void this.router.navigateByUrl(session.homePath);
        },
        error: (failure: HttpErrorResponse) => {
          this.submitting.set(false);
          const body = failure.error as ApiError | null;
          if (body?.code === 'SETUP_ALREADY_DONE') {
            void this.router.navigateByUrl('/login');
            return;
          }
          this.error.set(
            failure.status === 0
              ? 'Serveur injoignable. Vérifiez que Libero Shop est démarré.'
              : [body?.message, ...(body?.details ?? [])].filter(Boolean).join(' ') ||
                  'La création du compte a échoué.',
          );
        },
      });
  }
}
