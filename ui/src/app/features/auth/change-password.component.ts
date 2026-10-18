import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiError, MIN_PASSWORD_LENGTH } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * The signed-in person chooses their own password.
 *
 * <p>Reached two ways: from "Mon profil", by choice; and by the guards, for an account whose
 * password was handed out by the administrator — nothing else opens until it is replaced, so the
 * administrator stops knowing it. The current password is asked either way: a screen left open
 * must not let a passer-by lock its owner out.
 */
@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="setup-page">
      <form class="card setup-card" (ngSubmit)="submit()">
        <div class="setup-brand">
          <img src="logo.png" alt="" />
          <div>
            <div class="mark">Libero <span>Shop</span></div>
            <div class="sub">Mon mot de passe</div>
          </div>
        </div>

        @if (forced()) {
          <p class="setup-lead">
            Votre mot de passe actuel est provisoire : il vous a été donné. Choisissez maintenant
            le vôtre, vous serez seul à le connaître.
          </p>
        } @else {
          <p class="setup-lead">
            Choisissez un nouveau mot de passe. Vos autres appareils devront se reconnecter.
          </p>
        }

        <div class="fld">
          <label for="own-current">Mot de passe actuel</label>
          <input
            id="own-current"
            name="current"
            type="password"
            autocomplete="current-password"
            maxlength="72"
            autofocus
            [ngModel]="current()"
            (ngModelChange)="current.set($event)"
          />
        </div>
        <div class="fld">
          <label for="own-new">Nouveau mot de passe</label>
          <input
            id="own-new"
            name="next"
            type="password"
            autocomplete="new-password"
            maxlength="72"
            [ngModel]="next()"
            (ngModelChange)="next.set($event)"
          />
          <div class="hint muted">Au moins {{ minPasswordLength }} caractères.</div>
        </div>
        <div class="fld">
          <label for="own-confirm">Confirmer le nouveau mot de passe</label>
          <input
            id="own-confirm"
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
          {{ busy() ? 'Enregistrement...' : 'Changer mon mot de passe' }}
        </button>
        @if (forced()) {
          <button class="btn ghost block" type="button" style="margin-top:8px;" (click)="signOut()">
            Se déconnecter
          </button>
        } @else {
          <button class="btn ghost block" type="button" style="margin-top:8px;" (click)="auth.goHome()">
            Annuler
          </button>
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
        color: var(--brand-ink);
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
export class ChangePasswordComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  protected readonly minPasswordLength = MIN_PASSWORD_LENGTH;

  /** Sent here by the guards: the password in use is one the administrator chose. */
  protected readonly forced = computed(() => this.auth.state().mustChangePassword === true);

  protected readonly current = signal('');
  protected readonly next = signal('');
  protected readonly confirm = signal('');
  protected readonly busy = signal(false);
  /** What the server answered; cleared as soon as the form is sent again. */
  protected readonly error = signal<string | null>(null);

  /** What is wrong with the form as typed, said only once the field concerned has been filled. */
  protected readonly problem = computed<string | null>(() => {
    const next = this.next();
    if (next && next.length < MIN_PASSWORD_LENGTH) {
      return `Le mot de passe doit faire au moins ${MIN_PASSWORD_LENGTH} caractères.`;
    }
    if (next && next === this.current()) {
      return 'Le nouveau mot de passe doit être différent de l’actuel.';
    }
    if (this.confirm() && this.confirm() !== next) {
      return 'Les deux mots de passe ne sont pas identiques.';
    }
    return null;
  });

  protected canSubmit(): boolean {
    return (
      !this.busy() &&
      this.problem() === null &&
      this.current().length > 0 &&
      this.next().length > 0 &&
      this.confirm() === this.next()
    );
  }

  protected submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.auth.changePassword(this.current(), this.next()).subscribe({
      next: (session) => {
        this.busy.set(false);
        this.toasts.show('Mot de passe changé.');
        void this.router.navigateByUrl(session.homePath);
      },
      error: (failure: HttpErrorResponse) => {
        this.busy.set(false);
        const body = failure.error as ApiError | null;
        this.error.set(
          failure.status === 0
            ? 'Serveur injoignable. Vérifiez que Libero Shop est démarré.'
            : [body?.message, ...(body?.details ?? [])].filter(Boolean).join(' ') ||
                'Le mot de passe n’a pas pu être changé.',
        );
      },
    });
  }

  protected signOut(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/login'));
  }
}
