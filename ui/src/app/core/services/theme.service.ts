import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

/** What a person picks: a theme, or to follow their device's. */
export type ThemeChoice = 'light' | 'dark' | 'system';
export type Theme = 'light' | 'dark';

/** Shared with the script in index.html, which applies the theme before Angular starts. */
export const THEME_STORAGE_KEY = 'liberoshop.theme';

const DARK_QUERY = '(prefers-color-scheme: dark)';

/**
 * The light and dark themes: which one is on, and the choice behind it.
 *
 * <p>The themes themselves are only CSS — a set of variables per `data-theme` in styles.scss.
 * This service decides the attribute: the person's choice, kept per device (the till may stay
 * light while a waiter's phone goes dark), or, by default, the device's own setting, followed
 * live — a phone that turns dark at sunset takes the application with it.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly media = this.document.defaultView?.matchMedia(DARK_QUERY) ?? null;
  private readonly deviceDark = signal(this.media?.matches ?? false);

  readonly choice = signal<ThemeChoice>(readChoice());

  /** The theme actually shown. */
  readonly theme = computed<Theme>(() => {
    const choice = this.choice();
    return choice === 'system' ? (this.deviceDark() ? 'dark' : 'light') : choice;
  });

  constructor() {
    this.media?.addEventListener('change', (event) => this.deviceDark.set(event.matches));
    effect(() => {
      this.document.documentElement.setAttribute('data-theme', this.theme());
      writeChoice(this.choice());
    });
  }

  choose(choice: ThemeChoice): void {
    this.choice.set(choice);
  }
}

function readChoice(): ThemeChoice {
  try {
    const stored = localStorage.getItem(THEME_STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'system';
  } catch {
    // Storage refused (private mode): follow the device, and do not remember.
    return 'system';
  }
}

function writeChoice(choice: ThemeChoice): void {
  try {
    if (choice === 'system') {
      localStorage.removeItem(THEME_STORAGE_KEY);
    } else {
      localStorage.setItem(THEME_STORAGE_KEY, choice);
    }
  } catch {
    // Not remembered; the choice still holds for this visit.
  }
}
