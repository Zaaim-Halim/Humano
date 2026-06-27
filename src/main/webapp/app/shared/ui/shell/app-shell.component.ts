import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, booleanAttribute, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { filter } from 'rxjs/operators';

import { AvatarComponent } from '../data-display/avatar.component';

export type ShellChrome = 'app' | 'platform';

export interface NavItem {
  id: string;
  label: string;
  icon?: string;
  badge?: string | number;
  /** Router link; when set the item renders as a routerLink with active state. */
  link?: string | unknown[];
}

export interface NavGroup {
  heading?: string;
  items: NavItem[];
}

export interface ShellUser {
  name: string;
  role?: string;
  src?: string | null;
}

export interface ShellTenant {
  name: string;
  src?: string | null;
}

/**
 * AppShell — the product frame: left sidebar (brand, grouped nav, user footer)
 * + top bar (tenant switcher, search trigger, action icons), with page content
 * projected as the default slot. Set `chrome="platform"` for the superadmin
 * surface (violet-on-dark). Nav items can be router links (active state wired)
 * or fire `(navigate)` with `current` driving the active state. All visible
 * strings are inputs — pass already-translated values.
 * Mirrors `_ds_bundle.js` → AppShell.jsx.
 *
 * Slots: `[hum-topbar-actions]`, `[hum-sidebar-footer]`, default = main content.
 */
@Component({
  selector: 'hum-app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, RouterLink, RouterLinkActive, LucideAngularModule, AvatarComponent],
  host: {
    class: 'hum-shell',
    '[class.hum-shell--nav-open]': 'sidebarOpen()',
    '[attr.data-chrome]': 'chrome() === "platform" ? "platform" : null',
    '(document:keydown.escape)': 'closeNav()',
  },
  template: `
    <aside class="hum-side" id="hum-shell-sidebar">
      <div class="hum-side__brand">
        <span class="hum-side__wordmark">{{ brand() }}<span class="hum-side__dot" aria-hidden="true"></span></span>
      </div>

      <nav class="hum-side__nav" [attr.aria-label]="navLabel()">
        @for (group of nav(); track group.heading ?? $index) {
          <div class="hum-side__group">
            @if (group.heading; as heading) {
              <div class="hum-side__heading">{{ heading }}</div>
            }
            @for (it of group.items; track it.id) {
              @if (it.link) {
                <a
                  class="hum-side__item"
                  [routerLink]="it.link"
                  routerLinkActive
                  #rla="routerLinkActive"
                  [attr.aria-current]="rla.isActive ? 'page' : null"
                >
                  <ng-container [ngTemplateOutlet]="itemInner" [ngTemplateOutletContext]="{ $implicit: it }" />
                </a>
              } @else {
                <button
                  type="button"
                  class="hum-side__item"
                  [attr.aria-current]="current() === it.id ? 'page' : null"
                  (click)="navigate.emit(it.id)"
                >
                  <ng-container [ngTemplateOutlet]="itemInner" [ngTemplateOutletContext]="{ $implicit: it }" />
                </button>
              }
            }
          </div>
        }
      </nav>

      @if (user(); as u) {
        <div class="hum-side__footer">
          <div style="display:flex;align-items:center;gap:var(--space-2_5);padding:var(--space-1)">
            <hum-avatar [name]="u.name" [src]="u.src ?? null" size="sm" />
            <div style="flex:1;min-width:0">
              <div
                style="font-size:var(--text-sm);font-weight:var(--weight-medium);white-space:nowrap;overflow:hidden;text-overflow:ellipsis"
                [style.color]="chrome() === 'platform' ? 'var(--chrome-text)' : 'var(--text-strong)'"
              >
                {{ u.name }}
              </div>
              @if (u.role; as role) {
                <div style="font-size:var(--text-xs);color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
                  {{ role }}
                </div>
              }
            </div>
          </div>
        </div>
      } @else if (hasSidebarFooter()) {
        <div class="hum-side__footer"><ng-content select="[hum-sidebar-footer]" /></div>
      }
    </aside>

    <header class="hum-topbar">
      <button
        type="button"
        class="hum-topbar__menu"
        [attr.aria-label]="menuLabel()"
        [attr.aria-expanded]="sidebarOpen()"
        aria-controls="hum-shell-sidebar"
        (click)="toggleNav()"
      >
        <lucide-icon name="menu" [size]="18" />
      </button>
      @if (tenant(); as t) {
        <button type="button" class="hum-topbar__tenant" (click)="tenantClick.emit()">
          <hum-avatar [name]="t.name" [src]="t.src ?? null" size="xs" [square]="true" />
          <span>{{ t.name }}</span>
          <lucide-icon name="chevron-down" [size]="14" [style.opacity]="0.6" />
        </button>
      }
      <button type="button" class="hum-topbar__search" (click)="searchClick.emit()" [style.margin-left]="tenant() ? 'var(--space-2)' : '0'">
        <lucide-icon name="search" [size]="15" />
        <span style="flex:1;text-align:left">{{ searchPlaceholder() }}</span>
      </button>
      <div class="hum-topbar__actions" [style.margin-left]="tenant() ? '0' : 'auto'"><ng-content select="[hum-topbar-actions]" /></div>
    </header>

    <main class="hum-shell__main"><ng-content /></main>

    <!-- Scrim behind the mobile drawer; only visible at the collapsed breakpoint when open. -->
    <button type="button" class="hum-shell__backdrop" tabindex="-1" aria-hidden="true" (click)="closeNav()"></button>

    <ng-template #itemInner let-it>
      @if (it.icon; as name) {
        <span class="hum-side__item-icon" aria-hidden="true"><lucide-icon [name]="name" [size]="16" /></span>
      }
      <span style="flex:1">{{ it.label }}</span>
      @if (it.badge !== undefined && it.badge !== null) {
        <span class="hum-side__item-badge">{{ it.badge }}</span>
      }
    </ng-template>
  `,
})
export class AppShellComponent {
  readonly chrome = input<ShellChrome>('app');
  readonly brand = input('humano');
  readonly nav = input<NavGroup[]>([]);
  /** Active item id for non-router nav. */
  readonly current = input<string>();
  readonly tenant = input<ShellTenant>();
  readonly user = input<ShellUser>();
  readonly searchPlaceholder = input('Search');
  readonly navLabel = input('Primary');
  /** Accessible label for the mobile menu toggle (pass an already-translated string). */
  readonly menuLabel = input('Menu');
  readonly hasSidebarFooter = input(false, { transform: booleanAttribute });

  readonly navigate = output<string>();
  readonly searchClick = output();
  readonly tenantClick = output();

  /** Whether the off-canvas sidebar drawer is open (only meaningful below --shell-collapse). */
  protected readonly sidebarOpen = signal(false);

  constructor() {
    // Close the drawer on any navigation — covers nav-item taps, the ⌘K palette, and programmatic nav.
    inject(Router)
      .events.pipe(
        filter(e => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.sidebarOpen.set(false));
  }

  protected toggleNav(): void {
    this.sidebarOpen.update(open => !open);
  }

  protected closeNav(): void {
    this.sidebarOpen.set(false);
  }
}
