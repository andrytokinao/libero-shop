import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { RecoveryStarted, SetupApi } from '../../core/api/setup.api';
import { SUPPORT } from '../../core/config/publisher';
import { ApiError } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';

/** As the server asks: see RecoverAdminRequest. */
const MIN_PASSWORD_LENGTH = 8;

/**
 * The administrator's password is lost and no other administrator can hand out a new one.
 *
 * <p>Two steps, both accepted only at the server's computer: the server writes a code into a
 * file of that computer, then the code typed back lets a new password be chosen. From any other
 * device the page only explains where to go.
 *
 * <p>The page does not say where that file is: its link is on the login page, in front of
 * anyone at the till. The support team says it on the phone.
 */
@Component({
  selector: 'app-admin-recovery',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="setup-page">
      <form class="card setup-card" (ngSubmit)="submit()">
        <div class="setup-brand">
          <img src="logo.png" alt="" />
          <div>
            <div class="mark">Libero <span>Shop</span></div>
            <div class="sub">Mot de passe administrateur oublié</div>
          </div>
        </div>

        @if (elsewhere()) {
          <p class="setup-lead">
            Le mot de passe de l'administrateur se retrouve sur l'ordinateur où Libero Shop est
            installé, pas depuis un téléphone ni un autre poste.
          </p>
          <p class="setup-lead">Ouvrez Libero Shop sur cet ordinateur et revenez sur cette page.</p>
        } @else if (started()) {
          <p class="setup-lead">
            Un code à 8 chiffres vient d'être écrit dans un fichier de cet ordinateur, avec les
            identifiants des administrateurs.
          </p>
          <p class="setup-lead">
            <strong>Appelez le support de Libero Shop</strong> : il vous indiquera où lire ce code.
          </p>
          <p class="setup-support">
            Support — WhatsApp :
            <a [href]="support.whatsappLink" target="_blank" rel="noopener">{{ support.phone }}</a>
          </p>
          <p class="setup-lead muted">Le code est valable {{ started()?.minutes }} minutes, une seule fois.</p>

          <div class="fld">
            <label for="recovery-code">Code de secours</label>
            <input
              id="recovery-code"
              name="code"
              type="text"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="8"
              autofocus
              [ngModel]="code()"
              (ngModelChange)="code.set($event)"
            />
          </div>
          <div class="fld">
            <label for="recovery-username">Identifiant de l'administrateur</label>
            <input
              id="recovery-username"
              name="username"
              type="text"
              autocomplete="username"
              maxlength="30"
              [ngModel]="username()"
              (ngModelChange)="username.set($event)"
            />
          </div>
          <div class="fld">
            <label for="recovery-password">Nouveau mot de passe</label>
            <input
              id="recovery-password"
              name="password"
              type="password"
              autocomplete="new-password"
              maxlength="72"
              [ngModel]="password()"
              (ngModelChange)="password.set($event)"
            />
            <div class="hint muted">Au moins {{ minPasswordLength }} caractères.</div>
          </div>
          <div class="fld">
            <label for="recovery-confirm">Confirmer le mot de passe</label>
            <input
              id="recovery-confirm"
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
            {{ busy() ? 'Enregistrement...' : 'Changer le mot de passe' }}
          </button>
          <button class="btn ghost block" type="button" style="margin-top:8px;" [disabled]="busy()" (click)="start()">
            Demander un nouveau code
          </button>
        } @else {
          <p class="setup-lead">
            Cette procédure remplace le mot de passe d'un compte administrateur. Elle ne fonctionne
            que sur l'ordinateur où Libero Shop est installé.
          </p>
          <p class="setup-lead">
            Elle se fait avec le support de Libero Shop, qui vous guidera au téléphone :
            <a [href]="support.whatsappLink" target="_blank" rel="noopener">{{ support.phone }}</a>
            (WhatsApp).
          </p>
          <p class="setup-lead">
            Pour un autre compte (caisse, dépôt…), demandez plutôt un nouveau mot de passe à
            l'administrateur.
          </p>

          @if (error(); as message) {
            <div class="setup-error" role="alert">{{ message }}</div>
          }

          <button class="btn block" type="button" [disabled]="busy()" (click)="start()">
            {{ busy() ? 'Un instant...' : 'Commencer' }}
          </button>
        }

        <div class="setup-back muted"><a routerLink="/login">Retour à la connexion</a></div>
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
      max-width: 420px;
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

    .setup-support {
      font-size: 13.5px;
      font-weight: 600;
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

    .setup-back {
      font-size: 12.5px;
      text-align: center;
      margin-top: 14px;
    }
  `,
})
export class AdminRecoveryComponent {
  private readonly api = inject(SetupApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly minPasswordLength = MIN_PASSWORD_LENGTH;
  protected readonly support = SUPPORT;

  protected readonly started = signal<RecoveryStarted | null>(null);
  /** The server said this device is not its own computer. */
  protected readonly elsewhere = signal(false);
  protected readonly code = signal('');
  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly confirm = signal('');
  protected readonly busy = signal(false);
  /** What the server answered; cleared as soon as something is sent again. */
  protected readonly error = signal<string | null>(null);

  /** What is wrong with the form as typed, said only once the field concerned has been filled. */
  protected readonly problem = computed<string | null>(() => {
    const password = this.password();
    if (password && password.length < MIN_PASSWORD_LENGTH) {
      return `Le mot de passe doit faire au moins ${MIN_PASSWORD_LENGTH} caractères.`;
    }
    if (this.confirm() && this.confirm() !== password) {
      return 'Les deux mots de passe ne sont pas identiques.';
    }
    return null;
  });

  /** Asks the server for a code; a second call replaces the first code. */
  protected start(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.startRecovery().subscribe({
      next: (started) => {
        this.busy.set(false);
        this.code.set('');
        this.started.set(started);
      },
      error: (failure: HttpErrorResponse) => {
        this.busy.set(false);
        this.fail(failure, 'Le code de secours n’a pas pu être préparé.');
      },
    });
  }

  protected canSubmit(): boolean {
    return (
      !this.busy() &&
      this.problem() === null &&
      this.code().trim().length > 0 &&
      this.username().trim().length > 0 &&
      this.password().length > 0 &&
      this.confirm() === this.password()
    );
  }

  protected submit(): void {
    if (!this.started() || !this.canSubmit()) {
      return;
    }
    const credentials = { username: this.username().trim(), password: this.password() };
    this.busy.set(true);
    this.error.set(null);

    this.api
      .recoverAdmin({ code: this.code().trim(), ...credentials })
      // Signed in straight away, as after the first account: the two fields were just typed.
      .pipe(switchMap(() => this.auth.login(credentials)))
      .subscribe({
        next: (session) => {
          this.busy.set(false);
          void this.router.navigateByUrl(session.homePath);
        },
        error: (failure: HttpErrorResponse) => {
          this.busy.set(false);
          if ((failure.error as ApiError | null)?.code === 'RECOVERY_CODE_EXPIRED') {
            // Nothing left to type the code against: back to asking for one.
            this.started.set(null);
          }
          this.fail(failure, 'Le mot de passe n’a pas pu être changé.');
        },
      });
  }

  private fail(failure: HttpErrorResponse, fallback: string): void {
    const body = failure.error as ApiError | null;
    if (body?.code === 'RECOVERY_ONLY_ON_SERVER') {
      this.elsewhere.set(true);
      return;
    }
    this.error.set(
      failure.status === 0
        ? 'Serveur injoignable. Vérifiez que Libero Shop est démarré.'
        : [body?.message, ...(body?.details ?? [])].filter(Boolean).join(' ') || fallback,
    );
  }
}
