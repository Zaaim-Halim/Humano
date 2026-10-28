import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, Signal, booleanAttribute, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { IsActiveMatchOptions, NavigationEnd, Router, RouterLink, isActive } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { filter } from 'rxjs/operators';

import { AvatarComponent } from '../data-display/avatar.component';
import { LogoComponent } from '../data-display/logo.component';

export type ShellChrome = 'app' | 'platform';

const EXACT: IsActiveMatchOptions = { paths: 'exact', queryParams: 'ignored', fragment: 'ignored', matrixParams: 'ignored' };
const SUBSET: IsActiveMatchOptions = { paths: 'subset', queryParams: 'ignored', fragment: 'ignored', matrixParams: 'ignored' };

export interface NavItem {
  id: string;
  label: string;
  icon?: string;
  badge?: string | number;
  /** Router link; when set the item renders as a routerLink with active state. */
  link?: string | unknown[];
  /** Active only on this exact URL (for a section root such as an overview), not its children. */
  exact?: boolean;
  /** Other paths where this item is the current page too, e.g. a detail page reached from it. */
  activeFor?: string[];
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
  imports: [NgTemplateOutlet, RouterLink, LucideAngularModule, AvatarComponent, LogoComponent],
  host: {
    class: 'hum-shell',
    '[class.hum-shell--nav-open]': 'sidebarOpen()',
    '[attr.data-chrome]': 'chrome() === "platform" ? "platform" : null',
    '(document:keydown.escape)': 'closeNav()',
  },
  template: `
    <aside class="hum-side" id="hum-shell-sidebar">
      <div class="hum-side__brand">
        <hum-logo [name]="brand()" />
      </div>

      <nav class="hum-side__nav" [attr.aria-label]="navLabel()">
        @for (group of nav(); track group.heading ?? $index) {
          <div class="hum-side__group">
            @if (group.heading; as heading) {
              <div class="hum-side__heading">{{ heading }}</div>
            }
            @for (it of group.items; track it.id) {
              @if (it.link) {
                <a class="hum-side__item" [routerLink]="it.link" [attr.aria-current]="isCurrent(it) ? 'page' : null">
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
      <div class="hum-topbar__start">
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
      </div>
      <!-- Three columns: company | search (centred) | actions. -->
      <button type="button" class="hum-topbar__search" (click)="searchClick.emit()">
        <lucide-icon name="search" [size]="15" />
        <span style="flex:1;text-align:left">{{ searchPlaceholder() }}</span>
      </button>
      <div class="hum-topbar__actions"><ng-content select="[hum-topbar-actions]" /></div>
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

  private readonly router = inject(Router);
  /** The current path: read by `isCurrent` so the active item follows navigation. */
  private readonly path = signal(this.router.url.split(/[?#]/)[0]);

  /** Per linked item, whether the router is on its link (or under it, unless `exact`). */
  private readonly linkActive = computed(() => {
    const active = new Map<string, Signal<boolean>>();
    for (const it of this.nav().flatMap(g => g.items)) {
      if (!it.link) continue;
      const tree = this.router.createUrlTree(Array.isArray(it.link) ? it.link : [it.link]);
      active.set(it.id, isActive(tree, this.router, it.exact ? EXACT : SUBSET));
    }
    return active;
  });

  constructor() {
    // Close the drawer on any navigation — covers nav-item taps, the ⌘K palette, and programmatic nav.
    this.router.events
      .pipe(
        filter(e => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.sidebarOpen.set(false);
        this.path.set(this.router.url.split(/[?#]/)[0]);
      });
  }

  /**
   * The item is the current page: the URL is its link (or under it, unless `exact`), or one of its
   * `activeFor` paths. Computed here rather than with `routerLinkActive`, which clears any
   * `aria-current` it did not set and so would undo `activeFor`.
   */
  protected isCurrent(it: NavItem): boolean {
    const path = this.path();
    if (it.activeFor?.some(p => path === p || path.startsWith(p + '/'))) return true;
    return this.linkActive().get(it.id)?.() ?? false;
  }

  protected toggleNav(): void {
    this.sidebarOpen.update(open => !open);
  }

  protected closeNav(): void {
    this.sidebarOpen.set(false);
  }
}
