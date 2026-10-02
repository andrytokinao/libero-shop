import { Component, Signal, computed, inject, input, output, signal } from '@angular/core';
import { CatalogApi } from '../../core/api/catalog.api';
import { CategoryNode } from '../../core/models';
import { CategoryImages } from '../../core/services/category-images.service';
import { ToastService } from '../../core/services/toast.service';
import { squareThumbnail } from '../../core/utils/image.util';

/**
 * A rayon's picture in the category admin: what the sale screens' folder tile shows, with the
 * two things one does to it — choose another, or remove it.
 *
 * <p>Shrunk to a square in the browser before it leaves, like a profile photo: a phone picture of
 * several megabytes goes up as a thumbnail of a few tens of kilobytes. Emits the rayon as it now
 * stands, for the screen to put back in place.
 */
@Component({
  selector: 'app-category-image-picker',
  standalone: true,
  template: `
    <div class="cip">
      <button
        type="button"
        class="cip-thumb"
        [disabled]="busy()"
        [title]="rayon().imageVersion ? 'Changer l\\'image' : 'Ajouter une image'"
        (click)="file.click()"
      >
        @if (image(); as src) {
          <img [src]="src" alt="" />
        } @else {
          <span>{{ busy() ? '…' : '＋' }}</span>
        }
      </button>
      @if (rayon().imageVersion) {
        <button type="button" class="cip-remove" title="Retirer l'image" [disabled]="busy()" (click)="remove()">
          ✕
        </button>
      }
      <input #file type="file" accept="image/jpeg,image/png,image/webp" hidden (change)="chosen($event)" />
    </div>
  `,
  styles: `
    .cip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    .cip-thumb {
      width: 40px;
      height: 40px;
      padding: 0;
      border: 1px dashed var(--line);
      border-radius: 7px;
      overflow: hidden;
      background: var(--bg);
      color: var(--ink-soft);
      font: inherit;
      cursor: pointer;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .cip-remove {
      border: none;
      background: none;
      color: var(--red);
      cursor: pointer;
      font-size: 12px;
    }
  `,
})
export class CategoryImagePickerComponent {
  private readonly api = inject(CatalogApi);
  private readonly images = inject(CategoryImages);
  private readonly toasts = inject(ToastService);

  readonly rayon = input.required<CategoryNode>();
  /** The rayon with its new `imageVersion`, once the server has it. */
  readonly changed = output<CategoryNode>();

  protected readonly busy = signal(false);

  private static readonly NO_IMAGE: Signal<string | null> = signal(null);

  protected readonly image = computed(() => {
    const rayon = this.rayon();
    return (rayon.imageVersion == null
      ? CategoryImagePickerComponent.NO_IMAGE
      : this.images.url(rayon.id, rayon.imageVersion))();
  });

  protected async chosen(event: Event): Promise<void> {
    const field = event.target as HTMLInputElement;
    const file = field.files?.[0];
    field.value = '';
    if (!file) {
      return;
    }
    this.busy.set(true);
    let thumbnail: Blob;
    try {
      thumbnail = await squareThumbnail(file);
    } catch (error) {
      this.busy.set(false);
      this.toasts.show((error as Error).message);
      return;
    }
    this.api.uploadCategoryImage(this.rayon().id, thumbnail).subscribe({
      next: (rayon) => this.done(rayon),
      error: () => this.busy.set(false),
    });
  }

  protected remove(): void {
    this.busy.set(true);
    this.api.removeCategoryImage(this.rayon().id).subscribe({
      next: (rayon) => this.done(rayon),
      error: () => this.busy.set(false),
    });
  }

  private done(rayon: CategoryNode): void {
    this.busy.set(false);
    this.changed.emit(rayon);
  }
}
