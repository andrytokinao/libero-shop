import { Component, EventEmitter, Input, OnInit, Output, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CreateUserRequest,
  MIN_PASSWORD_LENGTH,
  RoleApp,
  USERNAME_PATTERN,
  UpdateUserRequest,
  UserApp,
} from '../../core/models';
import { ProfilePhotoPickerComponent } from '../../shared/components/profile-photo-picker.component';
import { RolePickerComponent } from './role-picker.component';

/**
 * Opening an account, or correcting one.
 *
 * <p>One dialog for both, because the questions are nearly the same and the roles are the
 * whole point of either. What differs is what cannot be changed afterwards: the login handle
 * and the first password are asked once, at creation, and the edit form does not show them —
 * the handle is fixed for good, and a password is set through its own dialog so a rename
 * cannot reset one by accident.
 *
 * <p>Nothing is decided here. The checks below only let the screen say what is wrong before
 * the round trip; the server validates the same payload again and owns the refusal.
 */
@Component({
  selector: 'app-user-dialog',
  standalone: true,
  imports: [FormsModule, RolePickerComponent, ProfilePhotoPickerComponent],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div class="modal-backdrop" (click)="onBackdrop($event)">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="user-dialog-title">
        <div class="modal-head">
          <div>
            <h2 id="user-dialog-title">
              {{ user ? 'Modifier le compte' : 'Nouveau compte' }}
            </h2>
            <div class="sub muted">
              @if (user) {
                Identifiant {{ user.username }} — non modifiable
              } @else {
                Le compte est actif dès sa création et peut se connecter aussitôt
              }
            </div>
          </div>
          <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
        </div>

        <div class="modal-body">
          @if (user) {
            <!-- Saved on its own, at once: a photo is not part of the form below. -->
            <div style="margin-bottom:16px;">
              <app-profile-photo-picker [user]="user" (changed)="photoChanged.emit($event)" />
            </div>
          }
          <div class="fld">
            <label for="user-fullname">Nom complet</label>
            <input
              id="user-fullname"
              type="text"
              autocomplete="off"
              placeholder="Fatima Randria"
              [ngModel]="fullName()"
              (ngModelChange)="fullName.set($event)"
            />
          </div>

          @if (!user) {
            <div class="fld">
              <label for="user-handle">Identifiant de connexion</label>
              <input
                id="user-handle"
                type="text"
                autocomplete="off"
                spellcheck="false"
                placeholder="fatima"
                [ngModel]="username()"
                (ngModelChange)="username.set($event)"
              />
              <div class="hint-line">
                Enregistré en minuscules, et définitif. Lettres, chiffres, point, tiret ou
                souligné, de 3 à 30 caractères.
              </div>
            </div>

            <div class="fld">
              <label for="user-password">Mot de passe initial</label>
              <div class="pwd">
                <input
                  id="user-password"
                  [type]="revealed() ? 'text' : 'password'"
                  autocomplete="new-password"
                  [ngModel]="password()"
                  (ngModelChange)="password.set($event)"
                />
                <button class="btn small ghost" type="button" (click)="revealed.set(!revealed())">
                  {{ revealed() ? 'Masquer' : 'Afficher' }}
                </button>
              </div>
              <div class="hint-line">
                {{ minPasswordLength }} caractères au minimum. À dicter à la personne, qui
                pourra le faire changer par un super admin.
              </div>
            </div>
          }

          <div class="fld" style="margin-top:14px;">
            <label>Rôles</label>
            <app-role-picker [selected]="roles()" (selectedChange)="roles.set($event)" />
            @if (user) {
              <div class="hint-line">
                Un rôle ajouté ou retiré prend effet à la prochaine connexion de la personne :
                les droits sont inscrits dans son jeton de session. Pour couper un accès
                immédiatement, désactivez le compte.
              </div>
            }
          </div>

          @if (problem(); as message) {
            <div class="dialog-problem">{{ message }}</div>
          }
        </div>

        <div class="modal-foot">
          <button class="btn ghost" type="button" (click)="closed.emit()">Annuler</button>
          <button class="btn" type="button" [disabled]="busy || !!problem()" (click)="submit()">
            {{ busy ? 'Enregistrement...' : user ? 'Enregistrer' : 'Créer le compte' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: `
    /* .fld, .hint-line and .dialog-problem are in the global stylesheet: every dialog is built
       out of them. Only what is peculiar to this form stays here. */

    /* The password field and its reveal button share the line, the field taking the slack. */
    .pwd {
      display: flex;
      gap: 8px;

      input {
        flex: 1;
      }
    }
  `,
})
export class UserDialogComponent implements OnInit {
  /** The account being corrected, or null to open a new one. */
  @Input() user: UserApp | null = null;
  /** Set while the parent's call is in flight, so the dialog cannot be submitted twice. */
  @Input() busy = false;
  @Output() readonly create = new EventEmitter<CreateUserRequest>();
  @Output() readonly update = new EventEmitter<UpdateUserRequest>();
  @Output() readonly closed = new EventEmitter<void>();
  /** The photo was set or removed — already saved, unlike the rest of the form. */
  @Output() readonly photoChanged = new EventEmitter<UserApp>();

  protected readonly minPasswordLength = MIN_PASSWORD_LENGTH;

  protected readonly fullName = signal('');
  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly roles = signal<RoleApp[]>([]);
  protected readonly revealed = signal(false);

  ngOnInit(): void {
    if (this.user) {
      this.fullName.set(this.user.fullName);
      this.roles.set([...this.user.roles]);
    }
  }

  /**
   * The first thing that is wrong, in the order the form is filled in — one message at a
   * time, because a list of four complaints under an empty form helps nobody.
   */
  protected readonly problem = computed<string | null>(() => {
    if (!this.fullName().trim()) {
      return 'Le nom complet est obligatoire.';
    }
    if (!this.user) {
      if (!USERNAME_PATTERN.test(this.username().trim())) {
        return "L'identifiant doit faire 3 à 30 caractères, sans espace ni accent.";
      }
      if (this.password().length < MIN_PASSWORD_LENGTH) {
        return `Le mot de passe doit faire au moins ${MIN_PASSWORD_LENGTH} caractères.`;
      }
    }
    if (!this.roles().length) {
      return 'Choisissez au moins un rôle.';
    }
    return null;
  });

  protected submit(): void {
    if (this.busy || this.problem()) {
      return;
    }
    if (this.user) {
      this.update.emit({ fullName: this.fullName().trim(), roles: this.roles() });
      return;
    }
    this.create.emit({
      fullName: this.fullName().trim(),
      username: this.username().trim(),
      password: this.password(),
      roles: this.roles(),
    });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
