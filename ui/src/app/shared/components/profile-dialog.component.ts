import { Component, inject, output } from '@angular/core';
import { ROLE_LABELS, UserApp } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ProfilePhotoPickerComponent } from './profile-photo-picker.component';

/**
 * "Mon profil": who is signed in, and their photo — the one thing about their account a person
 * changes themself. Name, roles and password stay the administrator's.
 */
@Component({
  selector: 'app-profile-dialog',
  standalone: true,
  imports: [ProfilePhotoPickerComponent],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    @if (auth.currentUser(); as me) {
      <div class="modal-backdrop" (click)="onBackdrop($event)">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="profile-title">
          <div class="modal-head">
            <div>
              <h2 id="profile-title">Mon profil</h2>
              <div class="sub muted">{{ me.fullName }} — {{ '@' + me.username }}</div>
            </div>
            <button class="x" type="button" aria-label="Fermer" (click)="closed.emit()">✕</button>
          </div>
          <div class="modal-body">
            <app-profile-photo-picker [user]="me" (changed)="applied($event)" />
            <p class="muted" style="margin:16px 0 0;">
              {{ rolesOf(me) }}. Votre photo apparaît partout où votre nom est affiché : ventes,
              remises, versements.
            </p>
          </div>
          <div class="modal-foot">
            <button class="btn ghost" type="button" (click)="closed.emit()">Fermer</button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ProfileDialogComponent {
  protected readonly auth = inject(AuthService);
  readonly closed = output<void>();

  protected applied(user: UserApp): void {
    this.auth.applyCurrentUser(user);
  }

  protected rolesOf(user: UserApp): string {
    return user.roles.map((role) => ROLE_LABELS[role]).join(', ');
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
