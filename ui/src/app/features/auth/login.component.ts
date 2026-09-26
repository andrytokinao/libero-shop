import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="login-page">
      <form class="card login-card" (ngSubmit)="submit()">
        <div class="login-brand">
          <div class="mark">Libero <span>Shop</span></div>
          <div class="sub">Gestion supermarche</div>
        </div>

        <div class="form-row" style="flex-direction:column; align-items:stretch;">
          <div class="fld">
            <label for="username">Identifiant</label>
            <input
              id="username"
              name="username"
              type="text"
              autocomplete="username"
              autofocus
              [ngModel]="username()"
              (ngModelChange)="username.set($event)"
            />
          </div>
          <div class="fld">
            <label for="password">Mot de passe</label>
            <input
              id="password"
              name="password"
              type="password"
              autocomplete="current-password"
              [ngModel]="password()"
              (ngModelChange)="password.set($event)"
            />
          </div>
        </div>

        @if (error(); as message) {
          <div class="login-error">{{ message }}</div>
        }

        <button class="btn block" type="submit" [disabled]="!canSubmit()">
          {{ submitting() ? 'Connexion...' : 'Se connecter' }}
        </button>
      </form>
    </div>
  `,
  styles: `
    .login-page {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--brand-dark);
      padding: 24px;
    }

    .login-card {
      width: 100%;
      max-width: 360px;
    }

    .login-brand {
      margin-bottom: 20px;

      .mark {
        font-size: 22px;
        font-weight: 700;
        color: var(--brand-dark);
      }

      .mark span {
        color: var(--brand);
      }

      .sub {
        font-size: 12.5px;
        color: var(--ink-soft);
        margin-top: 2px;
      }
    }

    .fld input {
      width: 100%;
    }

    .login-error {
      background: var(--red-soft);
      color: var(--red);
      border-radius: 7px;
      padding: 9px 11px;
      font-size: 12.5px;
      margin-bottom: 12px;
    }
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected canSubmit(): boolean {
    return !this.submitting() && this.username().trim().length > 0 && this.password().length > 0;
  }

  protected submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);

    this.auth.login({ username: this.username().trim(), password: this.password() }).subscribe({
      next: (session) => {
        this.submitting.set(false);
        // Back to the page that sent us here, or the role's own landing page.
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigateByUrl(returnUrl || session.homePath);
      },
      error: (failure: HttpErrorResponse) => {
        this.submitting.set(false);
        const body = failure.error as ApiError | null;
        this.error.set(
          failure.status === 0
            ? 'Serveur injoignable. Verifiez que le backend est demarre.'
            : (body?.message ?? 'Identifiant ou mot de passe incorrect.'),
        );
      },
    });
  }
}
