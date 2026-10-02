import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { apiResource } from '../../core/api/api-resource';
import { ServerApi } from '../../core/api/server.api';
import { RoleApp, ServerAddress, ServerConnection } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { QrCodeComponent } from '../../shared/components/qr-code.component';

/** Where the phone will be when it talks to the server. */
type Network = 'local' | 'internet';

/**
 * Getting the Android app, for anyone: two scans, one to install it, one to connect it.
 *
 * <p>Public, like the APK behind it — a new employee equips their phone before they have an
 * account — so it fills the viewport when signed out and sits in the shell when signed in, the
 * way the server screen does.
 *
 * <p>The first question is asked in plain words: is the phone on the shop's Wi-Fi, or elsewhere?
 * The two give different addresses, and a code for the wrong one is a phone that "does not work".
 * On the Wi-Fi the server cannot know which of its network cards the phones reach, so it lists
 * them all, the likeliest first, and the choice sits behind a selector most people never open.
 */
@Component({
  selector: 'app-mobile-download',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe, QrCodeComponent],
  template: `
    <div [class.download-page]="!auth.isAuthenticated()">
      <div class="download">
        @if (!auth.isAuthenticated()) {
          <div class="card brand">
            <div class="mark">Libero <span>Shop</span></div>
            <div class="sub">Application mobile Android</div>
          </div>
        }

        @if (connection(); as c) {
          <div class="card">
            <h2>Où sera le téléphone ?</h2>
            <div class="networks" role="radiogroup" aria-label="Réseau du téléphone">
              <button
                type="button"
                role="radio"
                class="network"
                [class.on]="network() === 'local'"
                [attr.aria-checked]="network() === 'local'"
                (click)="pickedNetwork.set('local')"
              >
                <span class="ic">📶</span>
                <strong>Dans la boutique (Wi-Fi)</strong>
                <span class="muted">
                  Le téléphone est connecté au même Wi-Fi que l'ordinateur de la boutique. Le plus
                  rapide, et fonctionne même sans Internet.
                </span>
              </button>
              <button
                type="button"
                role="radio"
                class="network"
                [class.on]="network() === 'internet'"
                [attr.aria-checked]="network() === 'internet'"
                (click)="pickedNetwork.set('internet')"
              >
                <span class="ic">🌐</span>
                <strong>Partout (Internet)</strong>
                <span class="muted">
                  Le téléphone est ailleurs : données mobiles 4G, à la maison, dans un autre dépôt.
                  Il faut une connexion Internet.
                </span>
              </button>
            </div>

            @if (network() === 'local' && c.addresses.length > 1) {
              <details class="advanced">
                <summary>Le téléphone ne trouve pas le serveur ? Changer d'adresse</summary>
                <select
                  class="field"
                  aria-label="Adresse du serveur sur le Wi-Fi"
                  [ngModel]="chosen()?.url"
                  (ngModelChange)="pickedUrl.set($event)"
                >
                  @for (address of c.addresses; track address.url) {
                    <option [ngValue]="address.url">
                      {{ address.url }} — {{ address.label
                      }}{{ address.likelyVirtual ? ' (virtuelle)' : '' }}
                    </option>
                  }
                </select>
                <div class="muted small">
                  Choisissez l'adresse de la carte réseau reliée au Wi-Fi de la boutique.
                </div>
              </details>
            }
          </div>

          @if (chosen(); as address) {
            <div class="grid g2">
              <div class="card step">
                <h2>1. Installer l'application</h2>
                @if (c.apk; as apk) {
                  <app-qr-code [value]="address.url + apk.path" alt="QR code de téléchargement" />
                  <div class="muted small">
                    Scannez avec l'appareil photo du téléphone, puis ouvrez le fichier téléchargé.
                    Android demandera d'autoriser l'installation depuis le navigateur.
                  </div>
                  <a class="btn" [href]="address.url + apk.path" download>
                    Télécharger l'application
                  </a>
                  <div class="muted small">
                    {{ apk.sizeBytes / 1048576 | number: '1.1-1' }} Mo · publiée le
                    {{ apk.updatedAt | date: 'dd/MM/yyyy HH:mm' }}
                  </div>
                } @else {
                  <div class="empty">
                    L'application n'est pas encore disponible sur ce serveur. Prévenez
                    l'administrateur.
                  </div>
                  @if (isAdmin()) {
                    <div class="muted small">
                      Copiez le fichier <code>libero-shop.apk</code> dans le dossier
                      <code>downloads</code> à côté du serveur (voir
                      <code>docs/mobile-android.md</code>), puis rechargez cette page.
                    </div>
                  }
                }
              </div>

              <div class="card step">
                <h2>2. Connecter l'application au serveur</h2>
                <app-qr-code [value]="address.connectionCode" alt="QR code de connexion" />
                <div class="muted small">
                  Ouvrez l'application, touchez « Scanner le QR code » et visez ce code. Plus tard :
                  menu › Serveur.
                </div>
                <div class="small">Ou saisissez : <strong>{{ address.url }}</strong></div>
              </div>
            </div>
          } @else {
            <div class="card empty">
              @if (network() === 'internet') {
                Le serveur n'est pas accessible depuis Internet : l'application ne fonctionne que
                dans la boutique, sur son Wi-Fi.
                @if (isAdmin()) {
                  <div class="muted small">
                    Pour l'ouvrir sur Internet, publiez le serveur (redirection de port sur la box
                    ou reverse proxy), puis renseignez son adresse dans
                    <code>liberoshop.mobile.public-url</code>.
                  </div>
                }
              } @else {
                Les adresses du Wi-Fi de la boutique ne s'affichent que sur un appareil connecté à ce
                Wi-Fi. Ouvrez cette page depuis un ordinateur ou un téléphone de la boutique.
              }
            </div>
          }
        } @else {
          <div class="empty">Chargement...</div>
        }

        @if (!auth.isAuthenticated()) {
          <div class="back"><a routerLink="/login">Retour à la connexion</a></div>
        }
      </div>
    </div>
  `,
  styles: `
    .download-page {
      min-height: 100vh;
      background: var(--chrome-bg);
      padding: 24px;
    }

    .download {
      max-width: 860px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .brand .mark {
      font-size: 22px;
      font-weight: 700;
      color: var(--brand-dark);

      span {
        color: var(--brand);
      }
    }

    .brand .sub {
      font-size: 12.5px;
      color: var(--ink-soft);
    }

    h2 {
      margin-top: 0;
    }

    .networks {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 12px;
    }

    .network {
      display: flex;
      flex-direction: column;
      gap: 6px;
      text-align: left;
      padding: 14px;
      border: 2px solid var(--line, #ddd);
      border-radius: 10px;
      background: none;
      font: inherit;
      color: inherit;
      cursor: pointer;

      .ic {
        font-size: 22px;
      }

      .muted {
        font-size: 12.5px;
      }

      &.on {
        border-color: var(--brand);
        background: var(--brand-soft);
      }
    }

    .advanced {
      margin-top: 12px;
      font-size: 12.5px;

      summary {
        cursor: pointer;
        color: var(--ink-soft);
      }

      select {
        margin: 8px 0 4px;
        width: 100%;
      }
    }

    .small {
      font-size: 12px;
    }

    .step {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
      text-align: center;
    }

    .back {
      text-align: center;
      font-size: 12.5px;

      a {
        color: var(--chrome-ink);
      }
    }
  `,
})
export class MobileDownloadComponent {
  private readonly api = inject(ServerApi);
  protected readonly auth = inject(AuthService);

  protected readonly connection = apiResource<ServerConnection | null>(null, () =>
    this.api.connection(),
  ).value;

  protected readonly pickedNetwork = signal<Network | null>(null);
  protected readonly pickedUrl = signal<string | null>(null);

  /**
   * The visitor's pick, or a guess until they make one: the Wi-Fi when the server listed its
   * local addresses (the visitor is on it), otherwise the Internet.
   */
  protected readonly network = computed<Network>(() => {
    const picked = this.pickedNetwork();
    if (picked) {
      return picked;
    }
    const c = this.connection();
    return !c?.addresses.length && c?.internet ? 'internet' : 'local';
  });

  protected readonly chosen = computed<ServerAddress | undefined>(() => {
    const c = this.connection();
    if (!c) {
      return undefined;
    }
    if (this.network() === 'internet') {
      return c.internet ?? undefined;
    }
    return c.addresses.find((address) => address.url === this.pickedUrl()) ?? c.addresses[0];
  });

  /** Only an administrator can act on "not published" or "not on the Internet": they get the how. */
  protected readonly isAdmin = computed(() => this.auth.roles().includes(RoleApp.SUPER_ADMIN));
}
