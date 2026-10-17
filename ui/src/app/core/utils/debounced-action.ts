import { DestroyRef, inject } from '@angular/core';

/** How long typing pauses before a list asks the server again. */
export const SEARCH_RELOAD_MS = 300;

/**
 * An action run once typing pauses: each call pushes it back, so "F-1031" typed in a search box
 * asks the server once, not six times. Dropped if the screen closes meanwhile. Must be called in
 * an injection context — a field initialiser.
 */
export function debouncedAction(action: () => void, delayMs = SEARCH_RELOAD_MS): () => void {
  let timer: ReturnType<typeof setTimeout> | undefined;
  inject(DestroyRef).onDestroy(() => clearTimeout(timer));
  return () => {
    clearTimeout(timer);
    timer = setTimeout(action, delayMs);
  };
}
