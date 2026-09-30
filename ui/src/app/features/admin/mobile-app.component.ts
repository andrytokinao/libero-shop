import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { ServerApi } from '../../core/api/server.api';
import { ServerConnection } from '../../core/models';
import { QrCodeComponent } from '../../shared/components/qr-code.component';

/**
 * Setting up a phone, in two scans: one to install the app, one to connect it to this server.
 *
 * <p>The server cannot know which of its addresses the phones can reach — a machine has a
 * Wi-Fi card, a cable, and often a virtual adapter or two — so it lists them all, the likeliest
 * first, and the administrator picks the one on the shop's Wi-Fi. Both codes follow the choice.
 */
@Component({
  selector: 'app-mobile-app',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, QrCodeComponent],
  template: `
    @if (connection(); as c) {
      @if (c.addresses.length) {
        <div class="card pick">
          <label for="mobile-address">Adresse du serveur sur le réseau de la boutique</label>
          <select
            id="mobile-address"
            class="field"
            [ngModel]="chosenUrl()"
            (ngModelChange)="pickedUrl.set($event)"
          >
            @for (address of c.addresses; track address.url) {
              <option [ngValue]="address.url">
                {{ address.url }} — {{ address.label }}{{ address.likelyVirtual ? ' (virtuelle)' : '' }}
              </option>
            }
          </select>
          <div class="muted hint">
            Choisissez l'adresse de la carte réseau reliée au Wi-Fi de la boutique. Les téléphones
            doivent être connectés à ce même Wi-Fi.
          </div>
        </div>

        @if (chosen(); as address) {
          <div class="grid g2" style="margin-top:16px;">
            <div class="card step">
              <h2>1. Installer l'application</h2>
              @if (c.apk; as apk) {
                <app-qr-code [value]="address.url + apk.path" alt="QR code de téléchargement" />
                <div class="muted small">
                  Scannez avec l'appareil photo du téléphone, puis ouvrez le fichier téléchargé.
                  Android demandera d'autoriser l'installation depuis le navigateur.
                </div>
                <div class="small">
                  <a [href]="address.url + apk.path">{{ address.url + apk.path }}</a><br />
                  {{ apk.sizeBytes / 1048576 | number: '1.1-1' }} Mo · publiée le
                  {{ apk.updatedAt | date: 'dd/MM/yyyy HH:mm' }}
                </div>
              } @else {
                <div class="empty">
                  Aucune application publiée. Copiez le fichier <code>libero-shop.apk</code> dans
                  le dossier <code>downloads</code> à côté du serveur (voir la documentation
                  <code>docs/mobile-android.md</code>), puis rechargez cette page.
                </div>
              }
            </div>

            <div class="card step">
              <h2>2. Connecter l'application à ce serveur</h2>
              <app-qr-code [value]="address.connectionCode" alt="QR code de connexion" />
              <div class="muted small">
                Au premier lancement, touchez « Scanner le QR code » et visez ce code. Plus tard :
                menu › Serveur.
              </div>
              <div class="small">Ou saisissez : <strong>{{ address.url }}</strong></div>
            </div>
          </div>
        }
      } @else {
        <div class="card empty">
          Ce serveur n'a aucune adresse sur un réseau local : les téléphones ne peuvent pas le
          joindre. Vérifiez qu'il est relié au réseau de la boutique.
        </div>
      }
    } @else {
      <div class="empty">Chargement...</div>
    }
  `,
  styles: `
    .pick {
      display: flex;
      flex-direction: column;
      gap: 8px;

      label {
        font-size: 11.5px;
        color: var(--ink-soft);
        font-weight: 600;
      }
    }

    .hint,
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
  `,
})
export class MobileAppComponent {
  private readonly api = inject(ServerApi);

  protected readonly connection = apiResource<ServerConnection | null>(null, () =>
    this.api.connection(),
  ).value;

  protected readonly pickedUrl = signal<string | null>(null);

  /** The operator's pick, or the server's best guess until they make one. */
  protected readonly chosenUrl = computed(
    () => this.pickedUrl() ?? this.connection()?.addresses[0]?.url ?? null,
  );
  protected readonly chosen = computed(() =>
    this.connection()?.addresses.find((address) => address.url === this.chosenUrl()),
  );
}
