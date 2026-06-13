import { DOCUMENT, Injectable, effect, inject, signal } from '@angular/core';

export type Theme = 'light' | 'dark';
export type Density = 'comfortable' | 'compact';
export type Chrome = 'app' | 'platform';

/**
 * Drives the runtime appearance switches the design system reads off the root
 * element: `data-theme` (light/dark), `data-density` (comfortable/compact) and
 * `data-chrome` (app/platform). State is held in signals; an effect mirrors it
 * onto <html> so tokens (_tokens.scss / humano-theme.css scopes) re-resolve.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>('light');
  readonly density = signal<Density>('comfortable');
  readonly chrome = signal<Chrome>('app');

  private readonly document = inject(DOCUMENT);

  constructor() {
    effect(() => {
      const root = this.document.documentElement;
      this.applyAttr(root, 'theme', this.theme(), 'dark');
      this.applyAttr(root, 'density', this.density(), 'compact');
      this.applyAttr(root, 'chrome', this.chrome(), 'platform');
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
}
