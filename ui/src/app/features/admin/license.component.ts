import { Component, computed, inject, signal } from '@angular/core';
import { LICENSE_PLAN_LABELS, LicenseState } from '../../core/models';
import { LicenseService } from '../../core/services/license.service';
import { ToastService } from '../../core/services/toast.service';
import { copyToClipboard } from '../../core/utils/clipboard.util';
import { KpiCardComponent } from '../../shared/components/kpi-card.component';

/**
 * Licence administration: what is installed, when it ends, and the two ways to renew it.
 *
 * <p>Laid out in the order the job is actually done. The customer reads the state, copies
 * the renewal code, sends it to the publisher, and pastes back the `.lic` that comes in
 * by e-mail — so those four things follow each other down the page. The online check sits
 * last because on most installations it is switched off.
 *
 * <p>Nothing here decides anything: a licence is accepted or refused by the server, which
 * checks the publisher's signature, that the file was issued for this very machine, and
 * that it expires later than the one already in place. The screen only carries the bytes.
 */
@Component({
  selector: 'app-license',
  standalone: true,
  imports: [KpiCardComponent],
  template: `
    @if (license.status(); as status) {
      <div class="grid g4">
        <app-kpi-card label="État" [value]="license.stateLabel()" [hint]="planLabel()" />
        <app-kpi-card [label]="expiryLabel()" [value]="expiryValue()" [hint]="remainingLabel()" />
        <app-kpi-card
          label="Avant blocage des écritures"
          [value]="blockingLabel()"
          hint="passage en lecture seule"
        />
        <app-kpi-card
          label="Enregistrement des ventes"
          [value]="status.writesAllowed ? 'Autorisé' : 'Refusé'"
          [hint]="status.writesAllowed ? 'fonctionnement normal' : 'consultation seule'"
        />
      </div>

      <!-- Where the installation sits on the ACTIVE -> GRACE -> READ_ONLY line. -->
      <div class="card" style="margin-top:16px;">
        <h2>Cycle de vie de la licence <small>{{ status.message }}</small></h2>
        <div class="lic-track" aria-hidden="true">
          <span [class.on]="status.state === LicenseState.ACTIVE" class="s-ok"></span>
          <span [class.on]="status.state === LicenseState.GRACE" class="s-warn"></span>
          <span [class.on]="!status.writesAllowed" class="s-bad"></span>
        </div>
        <div class="lic-track-legend">
          <span>Active — tout permis</span>
          <span>Tolérance — tout permis, avec alerte</span>
          <span>Lecture seule — écritures refusées</span>
        </div>
      </div>

      <div class="grid g2" style="margin-top:16px;">
        <div class="card">
          <h2>Installation <small>ce que la licence couvre</small></h2>
          <dl class="dl">
            <dt>Client</dt>
            <dd>{{ status.customerName ?? '—' }}</dd>
            <dt>Référence client</dt>
            <dd>{{ status.customerId ?? '—' }}</dd>
            <dt>Formule</dt>
            <dd>{{ planLabel() || '—' }}</dd>
            <dt>Licence installée</dt>
            <dd>
              @if (status.licensed) {
                <span class="badge green">Oui</span>
              } @else {
                <span class="badge amber">Non — période d'essai</span>
              }
            </dd>
            <dt>Empreinte du poste</dt>
            <dd class="mono">{{ status.machineFingerprint }}</dd>
          </dl>
          <button class="btn small ghost" type="button" (click)="copy(status.machineFingerprint)">
            Copier l'empreinte
          </button>
          <p class="hints" style="margin-top:12px;">
            L'empreinte identifie cet ordinateur. Elle ne contient aucune donnée personnelle
            et change si le poste est renommé ou réinstallé — auquel cas une nouvelle licence
            doit être émise.
          </p>
        </div>

        <div class="card">
          <h2>Code de renouvellement <small>à envoyer à l'éditeur</small></h2>
          @if (license.renewalCode(); as code) {
            <div class="code-box big">{{ code.renewalCode }}</div>
            <div class="form-row" style="margin:12px 0 0;">
              <button class="btn small" type="button" (click)="copy(code.renewalCode)">
                Copier le code
              </button>
              <button class="btn small ghost" type="button" (click)="license.loadRenewalCode()">
                Régénérer
              </button>
            </div>
            <ul class="hints">
              @for (line of code.instructions; track line) {
                <li>{{ line }}</li>
              }
            </ul>
          } @else {
            <div class="empty">Chargement du code…</div>
          }
        </div>
      </div>

      <div class="card" style="margin-top:16px;">
        <h2>
          Installer une licence
          <small>collez le contenu du fichier .lic reçu, ou choisissez le fichier</small>
        </h2>
        <textarea
          class="field lic-paste"
          rows="7"
          spellcheck="false"
          placeholder='{ "format": "libero-shop-license", "version": 1, ... }'
          [value]="content()"
          (input)="content.set($any($event.target).value)"
        ></textarea>
        <div class="form-row" style="margin:12px 0 0;">
          <button
            class="btn"
            type="button"
            [disabled]="license.busy() || !content().trim()"
            (click)="install()"
          >
            Installer la licence
          </button>
          <label class="btn ghost small file-btn">
            Choisir un fichier .lic
            <input type="file" accept=".lic,application/json,text/plain" (change)="onFile($event)" />
          </label>
          @if (content().trim()) {
            <button class="btn ghost small" type="button" (click)="content.set('')">Effacer</button>
          }
        </div>
        <p class="hints" style="margin-top:12px;">
          Le fichier est vérifié avant d'être écrit : signature de l'éditeur, empreinte de ce
          poste, et date d'expiration postérieure à la licence déjà installée. Un fichier qui
          ne remplit pas ces trois conditions est refusé sans rien changer.
        </p>
      </div>

      <div class="card" style="margin-top:16px;">
        <h2>Renouvellement en ligne <small>si l'installation y est raccordée</small></h2>
        <p class="hints" style="margin:0 0 12px;">
          Le renouvellement est tenté automatiquement en arrière-plan, et tous les jours dès
          que l'échéance approche. Ce bouton force une tentative immédiate, pour le client qui
          vient de payer et ne veut pas attendre la prochaine.
        </p>
        <div class="form-row" style="margin:0;">
          <button class="btn ghost" type="button" [disabled]="license.busy()" (click)="renew()">
            Vérifier maintenant
          </button>
          <button class="btn ghost" type="button" [disabled]="license.busy()" (click)="license.refresh()">
            Rafraîchir l'état
          </button>
        </div>
      </div>
    } @else {
      <div class="card"><div class="empty">Lecture de l'état de la licence…</div></div>
    }
  `,
})
export class LicenseComponent {
  protected readonly license = inject(LicenseService);
  private readonly toasts = inject(ToastService);

  protected readonly LicenseState = LicenseState;
  protected readonly content = signal('');

  constructor() {
    this.license.refresh();
    this.license.loadRenewalCode();
  }

  protected readonly planLabel = computed(() => {
    const plan = this.license.status()?.plan;
    return plan ? LICENSE_PLAN_LABELS[plan] : '';
  });

  protected expiryLabel(): string {
    return this.license.status()?.licensed ? 'Expire le' : "Fin de la période d'essai";
  }

  /**
   * The expiry date in French. Formatted here rather than with `| date` so the KPI card
   * keeps taking a plain string, and the null case reads as a dash instead of blank.
   */
  protected expiryValue(): string {
    const iso = this.license.status()?.expiresOn;
    return iso ? new Date(iso).toLocaleDateString('fr-FR') : '—';
  }

  /** "dans 21 jour(s)" or "il y a 3 jour(s)" — the sign of the count carries the tense. */
  protected remainingLabel(): string {
    const status = this.license.status();
    if (!status?.expiresOn) {
      return 'aucune licence installée';
    }
    const days = status.daysUntilExpiry;
    return days >= 0 ? `dans ${days} jour(s)` : `il y a ${-days} jour(s)`;
  }

  protected blockingLabel(): string {
    const status = this.license.status();
    if (!status) {
      return '—';
    }
    if (!status.writesAllowed) {
      return 'Déjà bloqué';
    }
    return `${status.daysUntilReadOnly} jour(s)`;
  }

  protected install(): void {
    const content = this.content().trim();
    if (!content) {
      return;
    }
    this.license.install(content).subscribe((status) => {
      this.content.set('');
      this.toasts.show(
        status.licensed
          ? `Licence installée. Valable jusqu'au ${status.expiresOn}.`
          : 'Fichier accepté, mais aucune licence active ne le suit.',
      );
    });
  }

  protected renew(): void {
    this.license.renew().subscribe((status) => {
      this.toasts.show(
        status.licensed
          ? `État mis à jour. Licence valable jusqu'au ${status.expiresOn}.`
          : "Aucune licence n'a été reçue du serveur d'émission.",
      );
    });
  }

  protected onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    const reader = new FileReader();
    reader.onload = () => this.content.set(String(reader.result ?? ''));
    reader.readAsText(file);
    // Cleared so picking the same file twice still fires a change event.
    input.value = '';
  }

  protected copy(value: string): void {
    void copyToClipboard(value).then((copied) =>
      this.toasts.show(
        copied
          ? 'Copié dans le presse-papiers.'
          : 'Copie impossible sur cette connexion : sélectionnez la valeur à la main.',
      ),
    );
  }
}
