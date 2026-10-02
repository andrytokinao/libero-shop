import { Injectable, Signal, effect, inject, untracked } from '@angular/core';
import { CatalogApi } from '../api/catalog.api';
import { AuthService } from './auth.service';
import { VersionedImages } from './versioned-images';

/** The rayons' pictures on screen, each fetched once — see {@link VersionedImages}. */
@Injectable({ providedIn: 'root' })
export class CategoryImages {
  private readonly images: VersionedImages;

  constructor() {
    const catalog = inject(CatalogApi);
    this.images = new VersionedImages((id, version) => catalog.categoryImage(id, version));
    const auth = inject(AuthService);
    effect(() => {
      if (!auth.isAuthenticated()) {
        untracked(() => this.images.clear());
      }
    });
  }

  /** The rayon's picture as a local URL, null while it loads or when it has none. */
  url(categoryId: number, version: number): Signal<string | null> {
    return this.images.url(categoryId, version);
  }
}
