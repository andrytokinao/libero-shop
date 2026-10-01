import { DatePipe } from '@angular/common';
import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiResource } from '../../core/api/api-resource';
import { API_BASE_URL } from '../../core/api/api.config';
import { BackupApi } from '../../core/api/backup.api';
import { SILENT_ERRORS } from '../../core/interceptors/error.interceptor';
import {
  BACKUP_KIND_LABELS,
  BackupConfig,
  BackupItem,
  BackupKind,
  BackupOverview,
  intervalLabel,
  sizeLabel,
} from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { saveBlob } from '../../core/utils/download.util';

const EMPTY: BackupOverview = {
  available: true,
  status: null,
  config: null,
  backups: [],
  intervals: [],
};

/** How long to wait for the server to come back after a restore before saying so. */
const RESTART_TIMEOUT_MS = 3 * 60_000;

/**
 * The database's backups, for the owner: are they working, what can be restored, and how they
 * are kept.
 *
 * <p>Three parts, in the order they are needed: the state at a glance — red when no backup has
 * succeeded for too long, or the USB key is gone; the backups themselves, to download or to
 * restore; then the settings. A restore asks once more, saying what will be lost, then follows
 * the server through its restart.
 */
@Component({
  selector: 'app-backups',
  standalone: true,
  imports: [DatePipe, FormsModule],
  template: `
    @if (!overview().available) {
      <div class="card">
        <h2>Sauvegardes indisponibles</h2>
        <p class="muted">
          La base de données n'est pas un fichier de ce serveur (base en mémoire
          ou serveur de base séparé) : il n'y a rien que l'application puisse
          copier.
        </p>
      </div>
    } @else {
      @if (overview().status; as s) {
        <div class="grid g4">
          <div class="card stat" [class.bad]="s.stale">
            <div class="label">Dernière sauvegarde réussie</div>
            <div class="value">
              {{
                s.lastSuccessAt
                  ? (s.lastSuccessAt | date: 'dd/MM HH:mm')
                  : 'Aucune'
              }}
            </div>
            <div class="hint">
              {{ s.stale ? 'Trop ancienne : vérifiez ci-dessous' : 'À jour' }}
            </div>
          </div>
          <div class="card stat">
            <div class="label">Prochaine automatique</div>
            <div class="value">
              {{
                s.nextAutomaticAt
                  ? (s.nextAutomaticAt | date: 'dd/MM HH:mm')
                  : 'Désactivée'
              }}
            </div>
            <div class="hint">
              {{ config() ? interval(config()!.intervalMinutes) : '' }}
            </div>
          </div>
          <div
            class="card stat"
            [class.bad]="s.secondaryConfigured && !s.secondaryReachable"
            [class.warn]="!s.secondaryConfigured"
          >
            <div class="label">Copie sur un 2e support</div>
            <div class="value">
              {{
                !s.secondaryConfigured
                  ? 'Non configurée'
                  : s.secondaryReachable
                    ? 'Active'
                    : 'Injoignable'
              }}
            </div>
            <div class="hint">
              {{
                !s.secondaryConfigured
                  ? 'Conseillé : clé USB ou autre disque'
                  : s.secondaryReachable
                    ? config()?.secondaryDirectory
                    : 'Clé USB débranchée ?'
              }}
            </div>
          </div>
          <div class="card stat">
            <div class="label">Base de données</div>
            <div class="value">{{ size(s.databaseSizeBytes) }}</div>
            <div class="hint">
              {{ size(s.freeSpaceBytes) }} libres sur le disque
            </div>
          </div>
        </div>

        @if (s.lastError) {
          <div class="alert">
            Échec le {{ s.lastErrorAt | date: 'dd/MM HH:mm' }} :
            {{ s.lastError }}
          </div>
        }
        @if (s.secondaryError) {
          <div class="alert">{{ s.secondaryError }}</div>
        }

        <!-- ---------------------------------------------------------------- the backups -->
        <div class="card" style="margin-top:16px;">
          <div class="head-row">
            <h2>
              Sauvegardes
              <small
                >{{ visible().length }} sur
                {{ overview().backups.length }}</small
              >
            </h2>
            <div class="head-actions">
              <select
                class="field"
                [ngModel]="kindFilter()"
                (ngModelChange)="kindFilter.set($event)"
                aria-label="Type"
              >
                <option [ngValue]="null">Tous les types</option>
                @for (k of kinds; track k) {
                  <option [ngValue]="k">{{ kindLabels[k] }}</option>
                }
              </select>
              <button
                class="btn"
                type="button"
                [disabled]="busy()"
                (click)="backupNow()"
              >
                {{ busy() ? 'Sauvegarde…' : 'Sauvegarder maintenant' }}
              </button>
            </div>
          </div>

          @if (visible().length) {
            <table class="inv-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Type</th>
                  <th class="num">Taille</th>
                  <th>Contrôle</th>
                  <th>2e support</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (b of visible(); track b.fileName) {
                  <tr>
                    <td>
                      <strong>{{ b.createdAt | date: 'dd/MM/y HH:mm' }}</strong>
                    </td>
                    <td>
                      <span
                        class="badge"
                        [class]="'badge ' + kindColour(b.kind)"
                        >{{ kindLabels[b.kind] }}</span
                      >
                    </td>
                    <td class="num">{{ size(b.sizeBytes) }}</td>
                    <td>
                      @if (b.verified) {
                        <span class="ok">✓ lisible</span>
                        <span class="muted small">
                          · {{ b.invoiceCount }} factures,
                          {{ b.productCount }} produits</span
                        >
                      } @else {
                        <span class="ko" [title]="b.note ?? ''"
                          >✗ non vérifiée</span
                        >
                      }
                    </td>
                    <td>{{ b.inSecondary ? '✓' : '—' }}</td>
                    <td class="row-actions">
                      <button
                        class="btn small ghost"
                        type="button"
                        (click)="download(b)"
                      >
                        Télécharger
                      </button>
                      <button
                        class="btn small ghost"
                        type="button"
                        [disabled]="!s.canRestore || !b.verified"
                        (click)="restoring.set(b)"
                      >
                        Restaurer
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>

            <ul class="inv-cards">
              @for (b of visible(); track b.fileName) {
                <li class="inv-card">
                  <div class="head">
                    <strong>{{ b.createdAt | date: 'dd/MM/y HH:mm' }}</strong>
                    <span
                      class="badge"
                      [class]="'badge ' + kindColour(b.kind)"
                      >{{ kindLabels[b.kind] }}</span
                    >
                  </div>
                  <div class="who small">
                    @if (b.verified) {
                      <span class="ok">✓ lisible</span> ·
                      {{ b.invoiceCount }} factures,
                      {{ b.productCount }} produits
                    } @else {
                      <span class="ko">✗ non vérifiée</span>
                    }
                    · {{ size(b.sizeBytes)
                    }}{{ b.inSecondary ? ' · copiée sur le 2e support' : '' }}
                  </div>
                  <div class="foot">
                    <button
                      class="btn small ghost"
                      type="button"
                      (click)="download(b)"
                    >
                      Télécharger
                    </button>
                    <button
                      class="btn small ghost"
                      type="button"
                      [disabled]="!s.canRestore || !b.verified"
                      (click)="restoring.set(b)"
                    >
                      Restaurer
                    </button>
                  </div>
                </li>
              }
            </ul>
          } @else {
            <div class="empty">Aucune sauvegarde pour le moment.</div>
          }
          @if (!s.canRestore) {
            <p class="muted small" style="margin:10px 0 0;">
              Restauration depuis l'écran indisponible : ce serveur n'a pas été
              lancé de façon à pouvoir redémarrer seul.
            </p>
          }
        </div>

        <!-- ---------------------------------------------------------------- settings -->
        @if (draft(); as d) {
          <div class="card" style="margin-top:16px;">
            <h2>
              Paramètres
              <small>enregistrés à côté de la base, pas dedans</small>
            </h2>

            <label class="check">
              <input
                type="checkbox"
                [ngModel]="d.enabled"
                (ngModelChange)="patch({ enabled: $event })"
              />
              <span
                ><strong>Sauvegardes automatiques</strong> — les sauvegardes
                manuelles restent toujours possibles</span
              >
            </label>

            <div class="form-row">
              <div class="fld">
                <label for="bk-interval">Fréquence</label>
                <select
                  id="bk-interval"
                  class="field"
                  [ngModel]="d.intervalMinutes"
                  (ngModelChange)="patch({ intervalMinutes: $event })"
                >
                  @for (m of overview().intervals; track m) {
                    <option [ngValue]="m">{{ interval(m) }}</option>
                  }
                </select>
              </div>
            </div>

            <h3>
              Rotation
              <small class="muted"
                >ce qui est gardé, le reste est supprimé automatiquement</small
              >
            </h3>
            <div class="form-row">
              <div class="fld num-fld">
                <label for="bk-recent">Dernières sauvegardes</label>
                <input
                  id="bk-recent"
                  type="number"
                  min="1"
                  max="500"
                  [ngModel]="d.keepRecent"
                  (ngModelChange)="patch({ keepRecent: +$event })"
                />
              </div>
              <div class="fld num-fld">
                <label for="bk-daily">Une par jour, sur (jours)</label>
                <input
                  id="bk-daily"
                  type="number"
                  min="0"
                  max="366"
                  [ngModel]="d.keepDaily"
                  (ngModelChange)="patch({ keepDaily: +$event })"
                />
              </div>
              <div class="fld num-fld">
                <label for="bk-monthly">Une par mois, sur (mois)</label>
                <input
                  id="bk-monthly"
                  type="number"
                  min="0"
                  max="120"
                  [ngModel]="d.keepMonthly"
                  (ngModelChange)="patch({ keepMonthly: +$event })"
                />
              </div>
              <div class="fld num-fld">
                <label for="bk-manual">Manuelles gardées</label>
                <input
                  id="bk-manual"
                  type="number"
                  min="1"
                  max="100"
                  [ngModel]="d.keepManual"
                  (ngModelChange)="patch({ keepManual: +$event })"
                />
              </div>
            </div>
            <p class="muted small">{{ retentionSummary() }}</p>

            <h3>Stockage</h3>
            <div class="form-row">
              <div class="fld" style="flex:1; min-width:240px;">
                <label for="bk-dir"
                  >Dossier des sauvegardes (sur ce serveur)</label
                >
                <input
                  id="bk-dir"
                  type="text"
                  [ngModel]="d.directory"
                  (ngModelChange)="patch({ directory: $event })"
                />
              </div>
              <div class="fld" style="flex:1; min-width:240px;">
                <label for="bk-second">Deuxième support (conseillé)</label>
                <input
                  id="bk-second"
                  type="text"
                  placeholder="Ex : E:\\Sauvegardes (clé USB) ou \\\\PC-BUREAU\\Sauvegardes"
                  [ngModel]="d.secondaryDirectory ?? ''"
                  (ngModelChange)="
                    patch({ secondaryDirectory: $event.trim() || null })
                  "
                />
              </div>
            </div>
            <p class="muted small">
              Chaque sauvegarde est copiée sur le deuxième support : si le
              disque du serveur lâche, ou si la machine est volée, les ventes
              restent récupérables. Une clé USB ou un disque débranché est
              signalé en rouge en haut de cette page.
            </p>

            <div class="foot-row">
              <span class="muted">{{
                dirty()
                  ? 'Modifications non enregistrées.'
                  : 'Paramètres en vigueur.'
              }}</span>
              <button
                class="btn"
                type="button"
                [disabled]="!dirty() || saving()"
                (click)="save()"
              >
                {{ saving() ? 'Enregistrement…' : 'Enregistrer' }}
              </button>
            </div>
          </div>
        }
      }
    }

    <!-- ---------------------------------------------------------------- restore -->
    @if (restoring(); as b) {
      <div class="modal-backdrop" (click)="onBackdrop($event)">
        <div
          class="modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="restore-title"
        >
          <div class="modal-head">
            <div>
              <h2 id="restore-title">
                Restaurer la sauvegarde du
                {{ b.createdAt | date: 'dd/MM/y à HH:mm' }} ?
              </h2>
              <div class="sub muted">
                {{ b.invoiceCount }} factures, {{ b.productCount }} produits
              </div>
            </div>
            <button
              class="x"
              type="button"
              aria-label="Fermer"
              (click)="restoring.set(null)"
            >
              ✕
            </button>
          </div>
          <div class="modal-body">
            <p class="danger-box">
              Tout ce qui a été enregistré
              <strong
                >après le {{ b.createdAt | date: 'dd/MM à HH:mm' }}</strong
              >
              — ventes, paiements, stock — disparaîtra de l'application.
            </p>
            <ul class="steps">
              <li>
                La base actuelle est d'abord sauvegardée : on peut revenir en
                arrière.
              </li>
              <li>
                Le serveur redémarre, environ une minute : tous les postes sont
                déconnectés.
              </li>
              <li>Chacun se reconnecte ensuite normalement.</li>
            </ul>
          </div>
          <div class="modal-foot">
            <button
              class="btn ghost"
              type="button"
              (click)="restoring.set(null)"
            >
              Annuler
            </button>
            <button
              class="btn danger-btn"
              type="button"
              [disabled]="busy()"
              (click)="restore(b)"
            >
              Restaurer et redémarrer
            </button>
          </div>
        </div>
      </div>
    }

    @if (waitingForRestart()) {
      <div class="modal-backdrop">
        <div
          class="modal"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="restart-title"
        >
          <div class="modal-body center">
            <h2 id="restart-title">Restauration en cours…</h2>
            <p class="muted">{{ restartMessage() }}</p>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
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
        word-break: break-all;
      }

      &.bad {
        border-color: var(--red);
        background: var(--red-soft);

        .value,
        .hint {
          color: var(--red);
        }
      }

      &.warn {
        border-color: var(--amber);
        background: var(--amber-soft);
      }
    }

    .alert {
      margin-top: 12px;
      padding: 10px 14px;
      border-radius: 8px;
      background: var(--red-soft);
      color: var(--red);
    }

    .head-row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 10px;

      h2 {
        margin: 0;
      }
    }

    .head-actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .row-actions {
      white-space: nowrap;

      .btn + .btn {
        margin-left: 6px;
      }
    }

    .ok {
      color: var(--brand-dark);
      font-weight: 600;
    }

    .ko {
      color: var(--red);
      font-weight: 600;
    }

    .small {
      font-size: 12.5px;
    }

    h3 {
      margin: 18px 0 8px;
      font-size: 14.5px;
    }

    .check {
      display: flex;
      gap: 10px;
      align-items: center;
      margin-bottom: 10px;
      cursor: pointer;

      input {
        width: 20px;
        height: 20px;
      }
    }

    .num-fld input {
      width: 110px;
    }

    .foot-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      margin-top: 14px;
    }

    .danger-box {
      margin: 0 0 12px;
      padding: 10px 12px;
      border-radius: 8px;
      background: var(--red-soft);
      color: var(--red);
    }

    .steps {
      margin: 0;
      padding-left: 18px;
      color: var(--ink-soft);
      font-size: 13.5px;
    }

    .danger-btn {
      background: var(--red);
      border-color: var(--red);
    }

    .center {
      text-align: center;
    }
  `,
})
export class BackupsComponent {
  private readonly api = inject(BackupApi);
  private readonly http = inject(HttpClient);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly kinds = Object.values(BackupKind);
  protected readonly kindLabels = BACKUP_KIND_LABELS;
  protected readonly interval = intervalLabel;
  protected readonly size = sizeLabel;

  protected readonly busy = signal(false);
  protected readonly saving = signal(false);
  protected readonly kindFilter = signal<BackupKind | null>(null);
  protected readonly restoring = signal<BackupItem | null>(null);
  protected readonly waitingForRestart = signal(false);
  protected readonly restartMessage = signal('');

  private readonly resource = apiResource<BackupOverview>(EMPTY, () =>
    this.api.overview(),
  );
  protected readonly overview = this.resource.value;
  protected readonly config = computed(() => this.overview().config);

  /** The settings being edited; null until the server's have arrived. */
  private readonly edits = signal<Partial<BackupConfig>>({});
  protected readonly draft = computed<BackupConfig | null>(() => {
    const saved = this.config();
    return saved ? { ...saved, ...this.edits() } : null;
  });
  protected readonly dirty = computed(() => {
    const saved = this.config();
    const draft = this.draft();
    return (
      !!saved &&
      !!draft &&
      (Object.keys(draft) as (keyof BackupConfig)[]).some(
        (k) => draft[k] !== saved[k],
      )
    );
  });

  protected readonly visible = computed(() => {
    const kind = this.kindFilter();
    return this.overview().backups.filter(
      (b) => kind === null || b.kind === kind,
    );
  });

  /** "Au plus ~66 sauvegardes : les 24 dernières, puis une par jour sur 30 jours…". */
  protected readonly retentionSummary = computed(() => {
    const d = this.draft();
    if (!d) {
      return '';
    }
    const max = d.keepRecent + d.keepDaily + d.keepMonthly + d.keepManual + 5;
    return (
      `Au plus ~${max} sauvegardes : les ${d.keepRecent} dernières, puis une par jour sur ${d.keepDaily} jours, ` +
      `une par mois sur ${d.keepMonthly} mois, ${d.keepManual} manuelles, et les 5 dernières copies de démarrage.`
    );
  });

  protected kindColour(kind: BackupKind): string {
    return kind === BackupKind.AUTOMATIC
      ? 'green'
      : kind === BackupKind.MANUAL
        ? 'blue'
        : 'grey';
  }

  protected patch(change: Partial<BackupConfig>): void {
    this.edits.update((current) => ({ ...current, ...change }));
  }

  protected backupNow(): void {
    this.busy.set(true);
    this.api.backupNow().subscribe({
      next: (b) => {
        this.busy.set(false);
        this.resource.reload();
        this.toasts.show(
          b.verified
            ? `Sauvegarde faite et vérifiée : ${b.invoiceCount} factures, ${sizeLabel(b.sizeBytes)}.`
            : 'Sauvegarde faite, mais illisible à la vérification : voir le détail.',
        );
      },
      error: () => {
        this.busy.set(false);
        this.resource.reload();
      },
    });
  }

  protected save(): void {
    const draft = this.draft();
    if (!draft) {
      return;
    }
    this.saving.set(true);
    this.api.updateSettings(draft).subscribe({
      next: () => {
        this.saving.set(false);
        this.edits.set({});
        this.resource.reload();
        this.toasts.show('Paramètres de sauvegarde enregistrés.');
      },
      error: () => this.saving.set(false),
    });
  }

  protected download(b: BackupItem): void {
    this.api
      .download(b.fileName)
      .subscribe((blob) => saveBlob(blob, b.fileName));
  }

  protected restore(b: BackupItem): void {
    this.busy.set(true);
    this.api.restore(b.fileName).subscribe({
      next: () => {
        this.busy.set(false);
        this.restoring.set(null);
        this.waitForRestart();
      },
      error: () => this.busy.set(false),
    });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.restoring.set(null);
    }
  }

  /**
   * Follows the server through its restart: down first, then up again on the restored database,
   * at which point the page reloads — the session check decides whether to sign in again.
   */
  private waitForRestart(): void {
    this.waitingForRestart.set(true);
    this.restartMessage.set(
      'Le serveur redémarre sur la sauvegarde choisie. Ne fermez pas cette page.',
    );
    const started = Date.now();
    let wentDown = false;
    // Up again — or not gone down yet: only trust "up" after a "down", or after a while.
    const answered = (): void => {
      if (wentDown || Date.now() - started > 20_000) {
        clearInterval(probe);
        window.location.reload();
      }
    };
    const probe = setInterval(() => {
      this.http
        .get(`${API_BASE_URL}/server/info`, {
          context: new HttpContext().set(SILENT_ERRORS, true),
        })
        .subscribe({
          next: answered,
          error: (error: HttpErrorResponse) => {
            // Any answer from the server means it is up: a 401 is the session from before the
            // restart, which a new signing key no longer accepts. Only no answer at all (0) or the
            // dev proxy's 5xx mean it is still down.
            if (error.status > 0 && error.status < 500) {
              answered();
              return;
            }
            wentDown = true;
            if (Date.now() - started > RESTART_TIMEOUT_MS) {
              clearInterval(probe);
              this.restartMessage.set(
                "Le serveur ne répond pas encore. S'il ne revient pas, redémarrez-le à la main : " +
                  'la restauration sera faite au démarrage.',
              );
            }
          },
        });
    }, 2000);
    this.destroyRef.onDestroy(() => clearInterval(probe));
  }
}
