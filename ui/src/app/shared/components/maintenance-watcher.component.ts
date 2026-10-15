import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { API_BASE_URL } from '../../core/api/api.config';
import { MaintenanceNotice, UpdateApi } from '../../core/api/update.api';
import { SILENT_ERRORS } from '../../core/interceptors/error.interceptor';
import { RealtimeService } from '../../core/realtime/realtime.service';

/** How long to wait for the server to come back after an update before saying so. */
const RESTART_TIMEOUT_MS = 3 * 60_000;

/**
 * Listens, on every screen, for the server's maintenance-update messages and acts on them.
 *
 * <p>When an automatic update is ready the server asks each till whether it can be cut off now:
 * this shows the question, and sends back the answer. When the server is actually going down it
 * shows the "coming back" overlay and reloads once it returns — the same end as a restore, but
 * driven from a broadcast rather than from the page that triggered it.
 *
 * <p>Lives in the shell so it is always listening; it draws nothing until a message arrives.
 */
@Component({
  selector: 'app-maintenance-watcher',
  standalone: true,
  template: `
    @if (prompt(); as p) {
      <div class="modal-backdrop">
        <div class="modal" role="alertdialog" aria-modal="true" aria-labelledby="maint-title">
          <div class="modal-body">
            <h2 id="maint-title">Mise à jour disponible</h2>
            <p class="muted">{{ p.message }}</p>
            <p class="muted">
              Le serveur s'arrêtera un instant pour l'installer. Confirmez seulement si ce poste
              peut être coupé maintenant.
            </p>
          </div>
          <div class="modal-foot">
            <button class="btn ghost" type="button" (click)="decline()">Pas maintenant</button>
            <button class="btn" type="button" (click)="confirm()">Oui, mettre à jour</button>
          </div>
        </div>
      </div>
    }

    @if (applying()) {
      <div class="modal-backdrop">
        <div class="modal" role="alertdialog" aria-modal="true" aria-labelledby="maint-applying">
          <div class="modal-body center">
            <h2 id="maint-applying">Mise à jour en cours…</h2>
            <p class="muted">{{ applyingMessage() }}</p>
          </div>
        </div>
      </div>
    }
  `,
})
export class MaintenanceWatcherComponent {
  private readonly realtime = inject(RealtimeService);
  private readonly api = inject(UpdateApi);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly prompt = signal<MaintenanceNotice | null>(null);
  protected readonly applying = signal(false);
  protected readonly applyingMessage = signal('');

  constructor() {
    this.realtime
      .topic<MaintenanceNotice>('maintenance')
      .pipe(takeUntilDestroyed())
      .subscribe((notice) => this.handle(notice));
  }

  private handle(notice: MaintenanceNotice): void {
    switch (notice.kind) {
      case 'CONFIRM_REQUEST':
        this.prompt.set(notice);
        break;
      case 'APPLYING':
        this.prompt.set(null);
        this.waitForRestart(notice.message);
        break;
      case 'CANCELLED':
        this.prompt.set(null);
        this.applying.set(false);
        break;
    }
  }

  protected confirm(): void {
    this.prompt.set(null);
    this.api.confirm().subscribe();
  }

  protected decline(): void {
    this.prompt.set(null);
    this.api.decline().subscribe();
  }

  private waitForRestart(message: string): void {
    if (this.applying()) {
      return;
    }
    this.applying.set(true);
    this.applyingMessage.set(message);
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
              this.applyingMessage.set(
                "Le serveur ne répond pas encore. S'il ne revient pas, redémarrez-le à la main.",
              );
            }
          },
        });
    }, 2000);
    this.destroyRef.onDestroy(() => clearInterval(probe));
  }
}
