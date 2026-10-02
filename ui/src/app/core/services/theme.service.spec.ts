import { TestBed } from '@angular/core/testing';
import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  const html = document.documentElement;

  afterEach(() => localStorage.removeItem(THEME_STORAGE_KEY));

  function start(stored: string | null): ThemeService {
    if (stored) {
      localStorage.setItem(THEME_STORAGE_KEY, stored);
    } else {
      localStorage.removeItem(THEME_STORAGE_KEY);
    }
    const service = TestBed.inject(ThemeService);
    TestBed.flushEffects();
    return service;
  }

  it('starts from the choice kept on this device', () => {
    const theme = start('dark');

    expect(theme.choice()).toBe('dark');
    expect(html.getAttribute('data-theme')).toBe('dark');
  });

  it('follows the device when nothing was chosen', () => {
    const theme = start(null);
    const deviceDark = matchMedia('(prefers-color-scheme: dark)').matches;

    expect(theme.choice()).toBe('system');
    expect(theme.theme()).toBe(deviceDark ? 'dark' : 'light');
  });

  it('applies a choice at once, keeps it, and forgets it for "system"', () => {
    const theme = start(null);

    theme.choose('light');
    TestBed.flushEffects();
    expect(html.getAttribute('data-theme')).toBe('light');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');

    theme.choose('system');
    TestBed.flushEffects();
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBeNull();
  });
});
