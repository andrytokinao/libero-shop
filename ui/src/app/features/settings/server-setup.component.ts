import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CodeScanner } from '../../core/config/code-scanner.service';
import { ServerConfig, ServerInfo } from '../../core/config/server-config.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * Which server this installation talks to.
 *
 * <p>Three ways in, one screen: the first launch of the mobile app (the guard sends it here
 * until an address is saved), the link under the login form when the address has moved, and
 * the settings once signed in. Signed out it fills the viewport like the login page; signed in
 * it sits in the shell like any other page.
 *
 * <p>Nothing is saved unless the server has answered and said it is Libero Shop: a typo, or the
 * address of the Wi-Fi box, is refused on the spot rather than discovered at the first sign-in.
 * Moving an account to another server signs it out — its token was issued by the old one.
 */
@Component({
  selector: 'app-server-setup',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div [class.setup-page]="!auth.isAuthenticated()">
      <form class="card setup-card" (ngSubmit)="testAndSave()">
        @if (!auth.isAuthenticated()) {
          <div class="setup-brand">
            <div class="mark">Libero <span>Shop</span></div>
            <div class="sub">Connexion au serveur de la boutique</div>
          </div>
        }

        <p class="muted intro">
          @if (config.isMobile) {
            Scannez le QR code « Connecter » de la page « Application mobile » (sur un ordinateur
            de la boutique), ou saisissez l'adresse du serveur.
          } @else {
            Cette version web est servie par le serveur lui-même : elle n'a normalement rien à
            configurer. Cet écran sert à l'application mobile.
          }
        </p>

        @if (config.host(); as host) {
          <div class="current">
            Serveur actuel : <strong>{{ host }}</strong>
          </div>
        }

        @if (scanner.available) {
          <button class="btn block" type="button" [disabled]="busy()" (click)="scan()">
            Scanner le QR code
          </button>
          <div class="or muted">ou</div>
        }

        <div class="fld">
          <label for="server-url">Adresse du serveur</label>
          <input
            id="server-url"
            name="server-url"
            type="url"
            inputmode="url"
            autocapitalize="off"
            autocomplete="off"
            placeholder="192.168.1.10:8080"
            [ngModel]="address()"
            (ngModelChange)="edit($event)"
          />
        </div>

        @if (error(); as message) {
          <div class="setup-error">{{ message }}</div>
        }
        @if (found(); as info) {
          <div class="setup-ok">{{ info.name }} {{ info.version }} — serveur trouvé.</div>
        }

        <button class="btn block" type="submit" [disabled]="busy() || !address().trim()">
          {{ busy() ? 'Test en cours...' : 'Tester et enregistrer' }}
        </button>

        <div class="links">
          @if (auth.isAuthenticated()) {
            <a routerLink="/">Retour</a>
          } @else if (!config.needsSetup()) {
            <a routerLink="/login">Retour à la connexion</a>
          }
          @if (!config.isMobile && config.serverUrl()) {
            <button class="linkish" type="button" (click)="useOwnOrigin()">
              Revenir au serveur d'origine
            </button>
          }
        </div>
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
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .setup-brand .mark {
      font-size: 22px;
      font-weight: 700;
      color: var(--brand-dark);

      span {
        color: var(--brand);
      }
    }

    .intro,
    .current {
      font-size: 13px;
      margin: 0;
    }

    .or {
      text-align: center;
      font-size: 12px;
    }

    .setup-error {
      color: var(--red);
      background: var(--red-soft);
      border-radius: 8px;
      padding: 8px 10px;
      font-size: 12.5px;
    }

    .setup-ok {
      color: var(--brand);
      background: var(--brand-soft);
      border-radius: 8px;
      padding: 8px 10px;
      font-size: 12.5px;
    }

    .links {
      display: flex;
      justify-content: space-between;
      font-size: 12.5px;
    }

    .linkish {
      border: none;
      background: none;
      color: var(--brand);
      cursor: pointer;
      padding: 0;
      font: inherit;
    }
  `,
})
export class ServerSetupComponent {
  protected readonly config = inject(ServerConfig);
  protected readonly scanner = inject(CodeScanner);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  protected readonly address = signal(this.config.serverUrl() ?? '');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly found = signal<ServerInfo | null>(null);

  private readonly changing = computed(() => {
    try {
      return ServerConfig.normalise(this.address()) !== this.config.serverUrl();
    } catch {
      return true;
    }
  });

  protected edit(value: string): void {
    this.address.set(value);
    this.error.set(null);
    this.found.set(null);
  }

  protected async scan(): Promise<void> {
    this.error.set(null);
    try {
      const text = await this.scanner.scan();
      if (text === null) {
        return;
      }
      this.address.set(ServerConfig.fromScan(text));
      await this.testAndSave();
    } catch (error) {
      this.error.set((error as Error).message);
    }
  }

  protected async testAndSave(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.error.set(null);
    this.found.set(null);
    let url: string;
    try {
      url = ServerConfig.normalise(this.address());
    } catch (error) {
      this.error.set((error as Error).message);
      return;
    }

    this.busy.set(true);
    try {
      const info = await firstValueFrom(this.config.probe(url));
      const moved = this.changing();
      await this.config.save(url);
      this.address.set(url);
      this.found.set(info);
      this.afterSave(moved);
    } catch (error) {
      this.error.set((error as Error).message);
    } finally {
      this.busy.set(false);
    }
  }

  protected async useOwnOrigin(): Promise<void> {
    await this.config.forget();
    this.address.set('');
    this.afterSave(true);
  }

  /**
   * A signed-in account moved to another server is signed out: its token means nothing there.
   * Otherwise the next step is the login page — the reason this screen was shown.
   */
  private afterSave(moved: boolean): void {
    if (this.auth.isAuthenticated() && moved) {
      this.auth.clear();
      this.toasts.show('Serveur changé : reconnectez-vous.');
      void this.router.navigateByUrl('/login');
    } else if (!this.auth.isAuthenticated()) {
      void this.router.navigateByUrl('/login');
    } else {
      this.toasts.show('Serveur vérifié.');
    }
  }
}
