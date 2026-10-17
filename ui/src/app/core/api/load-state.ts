import { Signal } from '@angular/core';

/**
 * Where a list read from the server stands, so a screen tells "Chargement…", "Aucune facture" and
 * "Impossible de charger" apart instead of saying "Aucune facture" in all three cases.
 */
export interface LoadState {
  /** A request is on its way. */
  readonly loading: Signal<boolean>;
  /** The server has answered at least once: an empty list is really empty. */
  readonly loaded: Signal<boolean>;
  /** The last request failed; what is shown, if anything, is the previous answer. */
  readonly failed: Signal<boolean>;
  reload(): void;
}
