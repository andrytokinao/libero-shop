import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { API_BASE_URL } from '../../core/api/api.config';
import { UpdateApi, UpdateStatus } from '../../core/api/update.api';
import { SILENT_ERRORS } from '../../core/interceptors/error.interceptor';

/** How long to wait for the server to come back after an update before saying so. */
const RESTART_TIMEOUT_MS = 3 * 60_000;

/**
 * Updating the application — the hidden page. No menu points here (reach it at
 * /admin/mise-a-jour); the super-admin uses it to install a new version without reinstalling the
 * server. Three ways, all in one place: drop a .lsu file by hand, press "check now" when a URL is
 * configured, or let the automatic channel do it on a schedule.
 *
 * <p>Nothing here decides anything: the server checks the publisher's signature, that the payload
 * matches it, and that the version is newer, before it replaces its own code and restarts. The
 * screen only carries the bytes and then follows the server through its restart, as a restore does.
 */
@Component({
  selector: 'app-update',
  standalone: true,
  template: `
    @if (status(); as s) {
      <div class="grid g4">
        <div class="card stat">
          <div class="label">Version installée</div>
          <div class="value">{{ s.currentVersion }}</div>
        </div>
        <div class="card stat">
          <div class="label">Mise à jour en place</div>
          <div class="value">{{ s.canApply ? 'Possible' : 'Indisponible' }}</div>
          @if (s.unavailableReason) {
            <div class="hint">{{ s.unavailableReason }}</div>
          }
        </div>
        <div class="card stat">
          <div class="label">Mise à jour automatique</div>
          <div class="value">{{ s.automaticEnabled ? 'Activée' : 'Désactivée' }}</div>
          <div class="hint">
            @if (s.automaticEnabled) {
              Vérification chaque jour à {{ s.checkTime }}.
            } @else if (!s.urlConfigured) {
              Aucune adresse de mise à jour configurée.
            } @else {
              Vérification désactivée.
            }
          </div>
        </div>
        <div class="card stat">
          <div class="label">Confirmation avant coupure</div>
          <div class="value">{{ s.requireConfirmation ? 'Oui' : 'Non' }}</div>
          <div class="hint">les écrans connectés sont prévenus avant l'arrêt</div>
        </div>
      </div>

      <!-- 1 — upload a file by hand -->
      <div class="card" style="margin-top:16px;">
        <h2>Installer un fichier de mise à jour</h2>
        <p class="muted">
          Choisissez le fichier <code>.lsu</code> fourni par l'éditeur. Le serveur vérifie sa
          signature puis redémarre sur la nouvelle version. N'installez un fichier que depuis une
          source de confiance.
        </p>

        <input
          #file
          type="file"
          class="upd-file"
          accept=".lsu"
          (change)="onFile($event)"
          aria-hidden="true"
          tabindex="-1"
        />

        <div class="row">
          <button class="btn ghost" type="button" [disabled]="busy()" (click)="file.click()">
            {{ chosen() ? '↻ Changer de fichier' : '⇧ Choisir un fichier .lsu' }}
          </button>
          @if (chosen(); as f) {
            <span class="muted">{{ f.name }} ({{ sizeLabel(f.size) }})</span>
          }
          <button
            class="btn"
            type="button"
            [disabled]="busy() || !chosen() || !s.canApply"
            (click)="install()"
          >
            {{ busy() ? 'Envoi…' : 'Installer et redémarrer' }}
          </button>
        </div>
        @if (!s.canApply) {
          <p class="hint">La mise à jour en place n'est pas possible ici : {{ s.unavailableReason }}</p>
        }
      </div>

      <!-- 2 — roll back to the previous version -->
      @if (s.canRollback) {
        <div class="card" style="margin-top:16px;">
          <h2>Revenir à la version précédente</h2>
          <p class="muted">
            La version qui précédait la dernière mise à jour a été conservée. Vous pouvez la
            remettre en place si la nouvelle pose problème ; le serveur redémarre.
          </p>
          <button class="btn ghost danger-btn" type="button" [disabled]="busy()" (click)="rollback()">
            Restaurer la version précédente
          </button>
        </div>
      }
    } @else {
      <div class="card"><p class="muted">Chargement…</p></div>
    }

    @if (waitingForRestart()) {
      <div class="modal-backdrop">
        <div class="modal" role="alertdialog" aria-modal="true" aria-labelledby="upd-restart">
          <div class="modal-body center">
            <h2 id="upd-restart">Mise à jour en cours…</h2>
            <p class="muted">{{ restartMessage() }}</p>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .upd-file {
      position: absolute;
      width: 1px;
      height: 1px;
      opacity: 0;
      pointer-events: none;
    }

    .row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      margin-top: 12px;
    }

    .stat {
      .label {
        font-size: 12px;
        color: var(--ink-soft);
      }

      .value {
        font-size: 20px;
        font-weight: 700;
        margin: 4px 0;
      }

      .hint {
        font-size: 12px;
        color: var(--ink-soft);
      }
    }
  `,
})
export class UpdateComponent {
  private readonly api = inject(UpdateApi);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly status = signal<UpdateStatus | null>(null);
  protected readonly chosen = signal<File | null>(null);
  protected readonly busy = signal(false);
  protected readonly waitingForRestart = signal(false);
  protected readonly restartMessage = signal('');

  constructor() {
    this.load();
  }

  private load(): void {
    this.api.status().subscribe((s) => this.status.set(s));
  }

  protected onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.chosen.set(input.files?.[0] ?? null);
  }

  protected install(): void {
    const file = this.chosen();
    if (!file || this.busy()) {
      return;
    }
    if (!confirm(`Installer « ${file.name} » ? Le serveur va s'arrêter puis redémarrer.`)) {
      return;
    }
    this.busy.set(true);
    this.api.upload(file).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.waitForRestart(`Installation de la version ${r.version}. Ne fermez pas cette page.`);
      },
      error: () => this.busy.set(false),
    });
  }

  protected rollback(): void {
    if (this.busy() || !confirm('Remettre la version précédente ? Le serveur va redémarrer.')) {
      return;
    }
    this.busy.set(true);
    this.api.rollback().subscribe({
      next: () => {
        this.busy.set(false);
        this.waitForRestart('Retour à la version précédente. Ne fermez pas cette page.');
      },
      error: () => this.busy.set(false),
    });
  }

  protected sizeLabel(bytes: number): string {
    const mb = bytes / (1024 * 1024);
    return mb >= 1 ? `${mb.toFixed(1)} Mo` : `${Math.max(1, Math.round(bytes / 1024))} Ko`;
  }

  /**
   * Follows the server through its restart: down first, then up again on the new version, at which
   * point the page reloads. Same shape as the restore screen.
   */
  private waitForRestart(message: string): void {
    this.waitingForRestart.set(true);
    this.restartMessage.set(message);
    const started = Date.now();
    let wentDown = false;
    const answered = (): void => {
      if (wentDown || Date.now() - started > 20_000) {
        clearInterval(probe);
        window.location.reload();
      }
    };
    const probe = setInterval(() => {
      this.http
        .get(`${API_BASE_URL}/server/info`, { context: new HttpContext().set(SILENT_ERRORS, true) })
        .subscribe({
          next: answered,
          error: (error: HttpErrorResponse) => {
            if (error.status > 0 && error.status < 500) {
              answered();
              return;
            }
            wentDown = true;
            if (Date.now() - started > RESTART_TIMEOUT_MS) {
              clearInterval(probe);
              this.restartMessage.set(
                "Le serveur ne répond pas encore. S'il ne revient pas, redémarrez-le à la main.",
              );
            }
          },
        });
    }, 2000);
    this.destroyRef.onDestroy(() => clearInterval(probe));
  }
}
