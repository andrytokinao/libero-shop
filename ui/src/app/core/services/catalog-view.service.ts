import { Injectable, signal } from '@angular/core';

/** How a sale screen lays its products out: tiles to tap, or lines to read. */
export type CatalogView = 'grid' | 'list';

export const CATALOG_VIEW_STORAGE_KEY = 'liberoshop.catalogView';

/**
 * Tiles or lines on the screens that sell — the seller's choice, kept per device like the theme:
 * a till worked from the keyboard may read lines while a waiter's phone keeps its tiles.
 *
 * <p>Tiles by default: what the screens showed before there was a choice.
 */
@Injectable({ providedIn: 'root' })
export class CatalogViewService {
  readonly view = signal<CatalogView>(readView());

  choose(view: CatalogView): void {
    this.view.set(view);
    try {
      localStorage.setItem(CATALOG_VIEW_STORAGE_KEY, view);
    } catch {
      // Not remembered; the choice still holds for this visit.
    }
  }
}

function readView(): CatalogView {
  try {
    return localStorage.getItem(CATALOG_VIEW_STORAGE_KEY) === 'list' ? 'list' : 'grid';
  } catch {
    // Storage refused (private mode): tiles, and do not remember.
    return 'grid';
  }
}
