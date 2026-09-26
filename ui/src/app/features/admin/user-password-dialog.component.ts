import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MIN_PASSWORD_LENGTH, SetPasswordRequest, UserApp } from '../../core/models';

/**
 * Handing a new password to someone else's account.
 *
 * <p>The old password is not asked for, because the person who needs a new one has usually
 * forgotten theirs. What stands in for it is who is doing it: only a super-admin reaches
 * here, and the server checks that again.
 *
 * <p>The field can be revealed on purpose. The administrator has to read this password out
 * to a colleague standing next to them, and a row of dots they cannot check is how a shop
 * ends up with someone locked out by a typo.
 */
@Component({
  selector: 'app-user-password-dialog',
  standalone: true,
  imports: [FormsModule],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="user-pwd-title">
        <div class="modal-head">
          <div>
            <h2 id="user-pwd-title">Mot de passe de {{ user.fullName }}</h2>
            <div class="sub muted">
              Identifiant {{ user.username }} — l'ancien mot de passe n'est pas demandé
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          <div class="fld">
            <label for="new-password">Nouveau mot de passe</label>
            <div class="pwd">
              <input
                id="new-password"
                [type]="revealed() ? 'text' : 'password'"
                autocomplete="new-password"
                autofocus
                [ngModel]="password()"
                (ngModelChange)="password.set($event)"
              />
              <button class="btn small ghost" type="button" (click)="revealed.set(!revealed())">
                {{ revealed() ? 'Masquer' : 'Afficher' }}
              </button>
            </div>
            <div class="hint-line">{{ minPasswordLength }} caractères au minimum.</div>
          </div>

          <p class="hints" style="margin:0;">
            Une session déjà ouverte n'est pas coupée : le jeton de connexion ne contient pas
            le mot de passe. Pour fermer l'accès de quelqu'un immédiatement, désactivez son
            compte — cela prend effet à sa requête suivante.
          </p>
        </div>

        <div class="modal-foot">
          <button class="btn ghost" type="button" (click)="closed.emit()">Annuler</button>
          <button class="btn" type="button" [disabled]="busy || !valid()" (click)="submit()">
            {{ busy ? 'Enregistrement...' : 'Définir le mot de passe' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .fld {
      display: flex;
      flex-direction: column;
      gap: 5px;
      margin-bottom: 14px;

      label {
        font-size: 11.5px;
        color: var(--ink-soft);
        font-weight: 600;
      }
    }

    .pwd {
      display: flex;
      gap: 8px;

      input {
        flex: 1;
      }
    }

    .hint-line {
      font-size: 11.5px;
      color: var(--ink-soft);
    }
  `,
})
export class UserPasswordDialogComponent {
  @Input({ required: true }) user!: UserApp;
  @Input() busy = false;
  @Output() readonly submitted = new EventEmitter<SetPasswordRequest>();
  @Output() readonly closed = new EventEmitter<void>();

  protected readonly minPasswordLength = MIN_PASSWORD_LENGTH;
  protected readonly password = signal('');
  protected readonly revealed = signal(false);

  protected readonly valid = computed(() => this.password().length >= MIN_PASSWORD_LENGTH);

  protected submit(): void {
    if (this.busy || !this.valid()) {
      return;
    }
    this.submitted.emit({ password: this.password() });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
