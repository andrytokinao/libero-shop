import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { PUBLISHER } from '../../core/config/publisher';
import { ServerConfig } from '../../core/config/server-config.service';
import { ApiError } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { IconName } from '../../core/ui/icons';
import { IconComponent } from '../../shared/components/icon.component';

interface Highlight {
  readonly icon: IconName;
  readonly title: string;
  readonly text: string;
}

/** What the software does, in the words of the person standing in the shop. */
const HIGHLIGHTS: readonly Highlight[] = [
  {
    icon: 'cart',
    title: 'Caisse et ventes',
    text: 'Vente rapide au comptoir, factures et encaissements suivis au jour le jour.',
  },
  {
    icon: 'warehouse',
    title: 'Stock et dépôt',
    text: 'Approvisionnements, niveaux de stock et alertes avant la rupture.',
  },
  {
    icon: 'phone',
    title: 'Commandes sur téléphone',
    text: 'Prise de commande depuis un téléphone Android, sur le Wi-Fi du magasin.',
  },
  {
    icon: 'shield',
    title: 'Vos données chez vous',
    text: 'Un compte par personne selon son rôle, et des sauvegardes sur votre propre poste.',
  },
];

const SUPPORT_PHONE = '+261 34 05 680 13';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <div class="login-page">
      <!-- Something to read beside the form: what the software is, and who to call. -->
      <aside class="login-about">
        <img class="about-logo" src="logo.png" alt="" />
        <h1>Libero <span>Shop</span></h1>
        <p class="lead">
          Le logiciel de gestion de votre magasin : la caisse, le stock et les commandes réunis
          au même endroit, sur votre propre réseau.
        </p>

        <ul class="highlights">
          @for (item of highlights; track item.title) {
            <li>
              <span class="ic"><app-icon [name]="item.icon" [size]="18" /></span>
              <div>
                <div class="title">{{ item.title }}</div>
                <div class="text">{{ item.text }}</div>
              </div>
            </li>
          }
        </ul>

        <div class="support">
          <div class="label">Équipe support — WhatsApp</div>
          <a [href]="supportLink" target="_blank" rel="noopener">{{ supportPhone }}</a>
          <div class="text">Une question, un blocage ? Écrivez-nous, nous vous répondons.</div>
        </div>

        <div class="publisher text">
          Développé par {{ publisher.name }} ·
          <a [href]="publisher.websiteLink" target="_blank" rel="noopener">{{ publisher.website }}</a>
          · <a [href]="publisher.emailLink">{{ publisher.email }}</a>
        </div>
      </aside>

      <form class="card login-card" (ngSubmit)="submit()">
        <div class="login-brand">
          <div class="mark"><img class="brand-logo" src="logo.png" alt="" />Libero <span>Shop</span></div>
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

        <!-- On a phone the server is a setting, and the login page is where a moved server
             shows up first: the way to fix it sits right here. -->
        @if (server.isMobile || server.serverUrl()) {
          <div class="login-server muted">
            Serveur : {{ server.host() ?? 'non configuré' }} ·
            <a routerLink="/serveur">Modifier</a>
          </div>
        }
        @if (!server.isMobile) {
          <!-- Only goes through at the server's computer, where a phone never is. -->
          <div class="login-server muted">
            <a routerLink="/secours">Mot de passe administrateur oublié ?</a>
          </div>
          <div class="login-server muted">
            <a routerLink="/application-mobile">Télécharger l'application mobile Android</a>
          </div>
        }
      </form>
    </div>
  `,
  styles: `
    .login-page {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 56px;
      background: var(--chrome-bg);
      padding: 24px;
    }

    .login-card {
      width: 100%;
      max-width: 360px;
      flex: 0 0 auto;
    }

    .login-about {
      max-width: 420px;
      color: var(--chrome-ink);

      .about-logo {
        display: block;
        width: 84px;
        height: 84px;
        border-radius: 18px;
        margin-bottom: 16px;
      }

      h1 {
        margin: 0 0 10px;
        font-size: 30px;
        font-weight: 700;

        span {
          color: var(--chrome-accent);
        }
      }

      .lead {
        margin: 0 0 24px;
        font-size: 14.5px;
        line-height: 1.6;
        color: var(--chrome-ink-soft);
      }

      .text {
        font-size: 12.5px;
        line-height: 1.5;
        color: var(--chrome-ink-soft);
      }
    }

    .highlights {
      list-style: none;
      margin: 0;
      padding: 0;

      li {
        display: flex;
        gap: 12px;
        margin-bottom: 16px;
      }

      .ic {
        flex: 0 0 auto;
        width: 34px;
        height: 34px;
        border-radius: 9px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(255, 255, 255, 0.08);
        color: var(--chrome-accent);
      }

      .title {
        font-size: 13.5px;
        font-weight: 600;
        margin-bottom: 2px;
      }
    }

    .support {
      margin-top: 24px;
      padding-top: 18px;
      border-top: 1px solid rgba(255, 255, 255, 0.12);

      .label {
        font-size: 11.5px;
        font-weight: 600;
        color: var(--chrome-ink-soft);
      }

      a {
        display: inline-block;
        margin: 4px 0;
        font-size: 19px;
        font-weight: 700;
        color: var(--chrome-accent);
        text-decoration: none;
        font-variant-numeric: tabular-nums;

        &:hover,
        &:focus-visible {
          text-decoration: underline;
        }
      }
    }

    .publisher {
      margin-top: 16px;

      a {
        color: inherit;
      }
    }

    /* No room for two columns: the form first, the reading under it. */
    @media (max-width: 900px) {
      .login-page {
        flex-direction: column-reverse;
        justify-content: flex-end;
        gap: 28px;
        padding-top: 40px;
      }

      .login-about {
        max-width: 360px;

        h1,
        .about-logo {
          display: none;
        }
      }
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

    .login-server {
      margin-top: 14px;
      font-size: 12px;
      text-align: center;
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
  protected readonly server = inject(ServerConfig);
  protected readonly highlights = HIGHLIGHTS;
  protected readonly publisher = PUBLISHER;
  protected readonly supportPhone = SUPPORT_PHONE;
  protected readonly supportLink = `https://wa.me/${SUPPORT_PHONE.replace(/\D/g, '')}`;
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
            ? this.server.isMobile
              ? `Serveur ${this.server.host() ?? ''} injoignable : vérifiez le Wi-Fi ou modifiez l'adresse ci-dessous.`
              : 'Serveur injoignable. Verifiez que le backend est demarre.'
            : (body?.message ?? 'Identifiant ou mot de passe incorrect.'),
        );
      },
    });
  }
}
