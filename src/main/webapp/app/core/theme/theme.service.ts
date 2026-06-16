import { DOCUMENT, Injectable, effect, inject, signal } from '@angular/core';

export type Theme = 'light' | 'dark';
export type Density = 'comfortable' | 'compact';
export type Chrome = 'app' | 'platform';

/** localStorage key the pre-paint script in index.html also reads/writes. */
const THEME_STORAGE_KEY = 'humano-theme';

/**
 * Drives the runtime appearance switches the design system reads off the root
 * element: `data-theme` (light/dark), `data-density` (comfortable/compact) and
 * `data-chrome` (app/platform). State is held in signals; an effect mirrors it
 * onto <html> so tokens (_tokens.scss / humano-theme.css scopes) re-resolve.
 *
 * The theme is persisted to `localStorage["humano-theme"]` and restored on
 * boot. A tiny inline script in index.html applies it before first paint to
 * avoid a flash; this service stays the source of truth at runtime.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.readStoredTheme());
  readonly density = signal<Density>('comfortable');
  readonly chrome = signal<Chrome>('app');

  private readonly document = inject(DOCUMENT);

  constructor() {
    effect(() => {
      const root = this.document.documentElement;
      const theme = this.theme();
      this.applyAttr(root, 'theme', theme, 'dark');
      this.applyAttr(root, 'density', this.density(), 'compact');
      this.applyAttr(root, 'chrome', this.chrome(), 'platform');
      this.persistTheme(theme);
    });
  }

  toggleTheme(): void {
    this.theme.update(t => (t === 'dark' ? 'light' : 'dark'));
  }

  setTheme(theme: Theme): void {
    this.theme.set(theme);
  }

  setDensity(density: Density): void {
    this.density.set(density);
  }

  setChrome(chrome: Chrome): void {
    this.chrome.set(chrome);
  }

  /** Set data-<name> only for the non-default value; otherwise remove it. */
  private applyAttr(root: HTMLElement, name: string, value: string, activeValue: string): void {
    if (value === activeValue) {
      root.setAttribute(`data-${name}`, value);
    } else {
      root.removeAttribute(`data-${name}`);
    }
  }

  /** Restore the persisted theme; defaults to light when unset or unavailable. */
  private readStoredTheme(): Theme {
    try {
      return localStorage.getItem(THEME_STORAGE_KEY) === 'dark' ? 'dark' : 'light';
    } catch {
      return 'light';
    }
  }

  private persistTheme(theme: Theme): void {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // Ignore storage failures (private mode, disabled storage) — runtime state still holds.
    }
  }
}
