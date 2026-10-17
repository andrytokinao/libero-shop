import { Component, input } from '@angular/core';
import { LoadState } from '../../core/api/load-state';

/**
 * What a list says while it has nothing to show yet, or when the server could not be reached:
 * "Chargement…" before the first answer, "Impossible de charger … [Réessayer]" after a failure.
 * Says nothing once the list is there, so the list or its own "Aucun …" message speaks.
 *
 * <p>A failure after a first answer keeps the rows on screen, with a line above them: figures a
 * few seconds old are better than a blank screen, as long as they are flagged.
 */
@Component({
  selector: 'app-load-state',
  standalone: true,
  template: `
    @if (of(); as state) {
      @if (state.failed()) {
        <div class="ls-failed" [class.empty]="!state.loaded()" role="alert">
          @if (state.loaded()) {
            Impossible de mettre à jour {{ what() }} : la liste affichée peut être ancienne.
          } @else {
            Impossible de charger {{ what() }}. Vérifiez la connexion au serveur.
          }
          <button class="btn small ghost" type="button" [disabled]="state.loading()" (click)="state.reload()">
            {{ state.loading() ? 'Nouvel essai…' : 'Réessayer' }}
          </button>
        </div>
      } @else if (!state.loaded()) {
        <div class="empty ls-loading" aria-busy="true">Chargement…</div>
      }
    }
  `,
  styles: `
    .ls-failed {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
      gap: 10px;
      margin: 8px 0;
      padding: 10px 12px;
      border-radius: 8px;
      background: var(--red-soft);
      color: var(--red);
      font-size: 14px;
    }

    .ls-loading {
      color: var(--ink-soft);
    }
  `,
})
export class LoadStateComponent {
  /** The list's state; nothing is shown without one. */
  readonly of = input<LoadState | null | undefined>(null);
  /** The list named in the sentences: "les factures". */
  readonly what = input('la liste');
}

/** Whether a list's "Aucun …" message may show: it is really empty, not still on its way. */
export function showsEmpty(state: LoadState | null | undefined): boolean {
  return !state || state.loaded();
}
