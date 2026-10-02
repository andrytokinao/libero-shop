import { Component, inject, input, output, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { UserPhotoApi } from '../../core/api/user-photo.api';
import { UserApp } from '../../core/models';
import { ToastService } from '../../core/services/toast.service';
import { squareThumbnail } from '../../core/utils/image.util';
import { UserAvatarComponent } from './user-avatar.component';

/**
 * An account's photo, with the two things one does to it: choose another, or remove it.
 *
 * <p>The picture is cut to a square and shrunk in the browser before it leaves, so a phone
 * photo of several megabytes goes up as a thumbnail of a few tens of kilobytes. On a phone the
 * file picker offers the camera too.
 *
 * <p>Used by the administrator on any account and by each person on their own; the server
 * decides who may. Emits the account as it now stands, for the screen to put back in place.
 */
@Component({
  selector: 'app-profile-photo-picker',
  standalone: true,
  imports: [UserAvatarComponent],
  template: `
    <div class="picker">
      <app-user-avatar [user]="user()" size="xl" [tooltip]="false" />
      <div class="actions">
        <button class="btn ghost" type="button" [disabled]="busy()" (click)="file.click()">
          {{ busy() ? 'Envoi…' : user().photoVersion ? 'Changer la photo' : 'Ajouter une photo' }}
        </button>
        @if (user().photoVersion) {
          <button class="btn ghost danger" type="button" [disabled]="busy()" (click)="remove()">
            Retirer
          </button>
        }
        <span class="muted hint">JPEG, PNG ou WebP — recadrée en carré.</span>
      </div>
      <input
        #file
        type="file"
        accept="image/jpeg,image/png,image/webp"
        hidden
        (change)="chosen($event)"
      />
    </div>
  `,
  styles: `
    .picker {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }

    .hint {
      flex-basis: 100%;
      font-size: 12px;
    }

    .danger {
      color: var(--red);
    }
  `,
})
export class ProfilePhotoPickerComponent {
  private readonly api = inject(UserPhotoApi);
  private readonly toasts = inject(ToastService);

  readonly user = input.required<UserApp>();
  /** The account with its new `photoVersion`, once the server has it. */
  readonly changed = output<UserApp>();

  protected readonly busy = signal(false);

  protected async chosen(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const picked = input.files?.[0];
    // Cleared at once, so picking the same file again still fires a change.
    input.value = '';
    if (!picked) {
      return;
    }
    this.busy.set(true);
    try {
      const thumbnail = await squareThumbnail(picked);
      this.send(this.api.upload(this.user().id, thumbnail), 'Photo enregistrée.');
    } catch (error) {
      this.busy.set(false);
      this.toasts.show((error as Error).message);
    }
  }

  protected remove(): void {
    this.busy.set(true);
    this.send(this.api.remove(this.user().id), 'Photo retirée.');
  }

  private send(request: Observable<UserApp>, done: string): void {
    request.subscribe({
      next: (user) => {
        this.busy.set(false);
        this.toasts.show(done);
        this.changed.emit(user);
      },
      // The interceptor has said why.
      error: () => this.busy.set(false),
    });
  }
}
