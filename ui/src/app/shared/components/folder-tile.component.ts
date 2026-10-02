import { Component, Signal, computed, inject, input, output, signal } from '@angular/core';
import { CatalogFolder } from '../../core/models';
import { CategoryImages } from '../../core/services/category-images.service';
import { FavoritesStore } from '../../core/store/favorites.store';

/**
 * A rayon as a folder of the sale screens' grid: its picture — chosen in the category admin —
 * or, without one, its initial; its name; what it holds. A tap opens it.
 *
 * <p>The same footprint as a product tile, so folders and products sit in one grid like folders
 * and files in an explorer. The star pins the rayon to the seller's favourites.
 */
@Component({
  selector: 'app-folder-tile',
  standalone: true,
  template: `
    <div class="ft">
      <button type="button" class="ft-card" [attr.aria-label]="'Ouvrir le rayon ' + folder().rayon.name"
              (click)="opened.emit(folder())">
        <span class="ft-picture" aria-hidden="true">
          @if (image(); as src) {
            <img [src]="src" alt="" />
          } @else {
            <span class="ft-initial">{{ initial() }}</span>
          }
        </span>
        <span class="ft-name">{{ folder().rayon.name }}</span>
        <span class="ft-holds">{{ holds() }}</span>
      </button>
      <button
        type="button"
        class="ft-pin"
        [class.on]="pinned()"
        [attr.aria-pressed]="pinned()"
        [title]="pinned() ? 'Retirer des favoris' : 'Ajouter ce rayon aux favoris'"
        (click)="favorites.toggleCategory(folder().rayon.id)"
      >
        {{ pinned() ? '★' : '☆' }}
      </button>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .ft {
      position: relative;
      height: 100%;
    }

    .ft-card {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 4px;
      width: 100%;
      height: 100%;
      padding: 10px;
      border: 1px solid var(--line);
      border-radius: 9px;
      background: var(--bg);
      color: inherit;
      font: inherit;
      text-align: left;
      cursor: pointer;

      &:hover {
        border-color: var(--brand);
      }
    }

    .ft-picture {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      aspect-ratio: 16 / 9;
      max-height: 84px;
      border-radius: 7px;
      overflow: hidden;
      background: var(--brand-soft);

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .ft-initial {
      font-size: 28px;
      font-weight: 700;
      color: var(--brand-dark);
    }

    .ft-name {
      font-weight: 600;
      font-size: 13px;
    }

    .ft-holds {
      font-size: 11.5px;
      color: var(--ink-soft);
    }

    .ft-pin {
      position: absolute;
      top: 6px;
      right: 6px;
      width: 26px;
      height: 26px;
      border: none;
      border-radius: 50%;
      background: var(--panel);
      color: var(--ink-soft);
      font-size: 16px;
      line-height: 1;
      cursor: pointer;

      &.on {
        color: var(--amber);
      }
    }
  `,
})
export class FolderTileComponent {
  private readonly images = inject(CategoryImages);
  protected readonly favorites = inject(FavoritesStore);

  readonly folder = input.required<CatalogFolder>();
  readonly opened = output<CatalogFolder>();

  private static readonly NO_IMAGE: Signal<string | null> = signal(null);

  /** The rayon's picture, null while it loads or when it has none. */
  protected readonly image = computed(() => {
    const rayon = this.folder().rayon;
    return (rayon.imageVersion == null
      ? FolderTileComponent.NO_IMAGE
      : this.images.url(rayon.id, rayon.imageVersion))();
  });

  protected readonly initial = computed(() => this.folder().rayon.name.trim().charAt(0).toUpperCase());

  protected readonly pinned = computed(() => this.favorites.isCategoryPinned(this.folder().rayon.id));

  /** "3 rayons · 120 produits", the parts that apply. */
  protected readonly holds = computed(() => {
    const { folders, products } = this.folder();
    const parts = [];
    if (folders) {
      parts.push(`${folders} rayon${folders > 1 ? 's' : ''}`);
    }
    parts.push(`${products} produit${products > 1 ? 's' : ''}`);
    return parts.join(' · ');
  });
}
