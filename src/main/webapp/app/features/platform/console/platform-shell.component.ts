import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { merge } from 'rxjs';

import { Authority } from 'app/config/authority.constants';
import { AccountService } from 'app/core/auth/account.service';
import { ThemeService } from 'app/core/theme/theme.service';
import { legalMenuItems, openLegalPage } from 'app/features/legal/legal-menu';
import { LoginService } from 'app/login/login.service';
import {
  AppShellComponent,
  AvatarComponent,
  Command,
  CommandPaletteComponent,
  IconButtonComponent,
  MenuComponent,
  MenuItem,
  NavGroup,
  ShellTenant,
  ShellUser,
} from 'app/shared/ui';

interface ConsoleNavItem {
  id: string;
  labelKey: string;
  icon: string;
  link: string;
  keywords?: string;
  exact?: boolean;
}

/** The console navigation: one source for the sidebar and the ⌘K palette. */
const CONSOLE_NAV: { headingKey?: string; items: ConsoleNavItem[] }[] = [
  {
    items: [
      {
        id: 'overview',
        labelKey: 'humano.console.nav.overview',
        icon: 'layout-dashboard',
        link: '/platform',
        keywords: 'kpi mrr arr report',
        exact: true,
      },
      {
        id: 'customers',
        labelKey: 'humano.console.nav.customers',
        icon: 'building-2',
        link: '/platform/customers',
        keywords: 'tenants companies',
      },
      {
        id: 'demos',
        labelKey: 'humano.console.nav.demos',
        icon: 'presentation',
        link: '/platform/demos',
        keywords: 'demo requests prospects leads trial sales',
      },
    ],
  },
  {
    headingKey: 'humano.console.nav.groups.revenue',
    items: [
      {
        id: 'invoices',
        labelKey: 'humano.console.nav.invoices',
        icon: 'receipt',
        link: '/platform/billing/invoices',
        keywords: 'billing payments',
      },
      {
        id: 'plans',
        labelKey: 'humano.console.nav.plans',
        icon: 'layers',
        link: '/platform/billing/plans',
        keywords: 'pricing subscription',
      },
      {
        id: 'coupons',
        labelKey: 'humano.console.nav.coupons',
        icon: 'ticket',
        link: '/platform/billing/coupons',
        keywords: 'discount promo',
      },
      {
        id: 'addons',
        labelKey: 'humano.console.nav.addons',
        icon: 'hard-drive',
        link: '/platform/billing/addons',
        keywords: 'extra storage add-on packs',
      },
    ],
  },
  {
    headingKey: 'humano.console.nav.groups.admin',
    items: [
      {
        id: 'compliance',
        labelKey: 'humano.console.nav.compliance',
        icon: 'shield',
        link: '/platform/compliance',
        keywords: 'gdpr privacy retention deletion subprocessor dpa legal',
      },
      { id: 'team', labelKey: 'humano.console.nav.team', icon: 'users', link: '/platform/team', keywords: 'staff users invite' },
    ],
  },
];

/**
 * The SaaS owner console frame — a separate world from the company workspace: violet-on-dark
 * platform chrome, its own navigation (overview, customers, revenue, team) and no HR features.
 * Only platform accounts reach it (`platformGuard`); they are kept out of the workspace shell.
 */
@Component({
  selector: 'hum-platform-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, TranslatePipe, AppShellComponent, CommandPaletteComponent, IconButtonComponent, MenuComponent, AvatarComponent],
  template: `
    <hum-app-shell
      chrome="platform"
      [nav]="nav()"
      [tenant]="tenant()"
      [user]="user()"
      [searchPlaceholder]="'humano.console.search' | translate"
      [menuLabel]="'humano.shell.menu' | translate"
      (searchClick)="palette.openPalette()"
    >
      <div hum-topbar-actions style="display:flex;align-items:center;gap:var(--space-1)">
        <hum-icon-button
          [icon]="theme.theme() === 'dark' ? 'sun' : 'moon'"
          [label]="'humano.shell.toggleTheme' | translate"
          (click)="theme.toggleTheme()"
        />
        @if (user(); as u) {
          <div style="position:relative" (keydown.escape)="menuOpen.set(false)">
            <button
              type="button"
              class="hum-btn hum-btn--icon hum-btn--ghost"
              [attr.aria-label]="'humano.shell.accountMenu' | translate: { name: u.name }"
              aria-haspopup="menu"
              [attr.aria-expanded]="menuOpen()"
              (click)="menuOpen.set(!menuOpen())"
            >
              <hum-avatar [name]="u.name" [src]="u.src ?? null" size="xs" />
            </button>
            @if (menuOpen()) {
              <button
                type="button"
                aria-hidden="true"
                tabindex="-1"
                (click)="menuOpen.set(false)"
                style="position:fixed;inset:0;z-index:55;background:transparent;border:0;cursor:default"
              ></button>
              <div style="position:absolute;right:0;top:calc(100% + var(--space-1_5));z-index:60">
                <hum-menu [items]="userMenu()" (selected)="onMenu($event)" />
              </div>
            }
          </div>
        }
      </div>
      <router-outlet />
    </hum-app-shell>

    <hum-command-palette
      #palette
      [placeholder]="'humano.shell.searchCommands' | translate"
      [commands]="commands()"
      (run)="onCommand($event)"
    />
  `,
})
export default class PlatformShellComponent {
  protected readonly theme = inject(ThemeService);
  private readonly accountService = inject(AccountService);
  private readonly loginService = inject(LoginService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  private readonly account = this.accountService.trackCurrentAccount();
  protected readonly menuOpen = signal(false);
  private readonly isOwner = computed(() => !!this.account()?.authorities.includes(Authority.PLATFORM_OWNER));
  /** Re-runs the label computeds when translations load or change. */
  private readonly i18nTick = toSignal(merge(this.translate.onLangChange, this.translate.onTranslationChange), { initialValue: null });

  protected readonly tenant = computed<ShellTenant>(() => (this.i18nTick(), { name: this.t('humano.console.name') }));

  protected readonly user = computed<ShellUser | undefined>(() => {
    this.i18nTick();
    const a = this.account();
    if (!a) return undefined;
    return {
      name: [a.firstName, a.lastName].filter(Boolean).join(' ') || a.login,
      role: this.t(this.isOwner() ? 'humano.console.roles.ROLE_PLATFORM_OWNER' : 'humano.console.roles.ROLE_PLATFORM_ADMIN'),
      src: a.imageUrl,
    };
  });

  private readonly visible = computed(() => CONSOLE_NAV);

  protected readonly nav = computed<NavGroup[]>(() => {
    this.i18nTick();
    return this.visible().map(g => ({
      heading: g.headingKey ? this.t(g.headingKey) : undefined,
      items: g.items.map(i => ({ id: i.id, label: this.t(i.labelKey), icon: i.icon, link: i.link, exact: i.exact })),
    }));
  });

  protected readonly commands = computed<Command[]>(() => {
    this.i18nTick();
    return this.visible().flatMap(g =>
      g.items.map(i => ({
        id: i.id,
        label: this.t(i.labelKey),
        icon: i.icon,
        group: this.t(g.headingKey ?? 'humano.console.name'),
        keywords: i.keywords,
      })),
    );
  });

  protected readonly userMenu = computed<MenuItem[]>(() => {
    this.i18nTick();
    return [
      { heading: this.t('humano.shell.account') },
      { id: 'account', label: this.t('humano.shell.settings'), icon: 'settings' },
      { separator: true },
      ...legalMenuItems(this.t.bind(this)),
      { separator: true },
      { id: 'logout', label: this.t('humano.shell.logout'), icon: 'log-out', danger: true },
    ];
  });

  constructor() {
    // The platform chrome also tints anything rendered outside the shell (dialogs, palette).
    this.theme.setChrome('platform');
    inject(DestroyRef).onDestroy(() => this.theme.setChrome('app'));
  }

  protected onCommand(id: string): void {
    const item = this.visible()
      .flatMap(g => g.items)
      .find(i => i.id === id);
    if (item) void this.router.navigateByUrl(item.link);
  }

  protected onMenu(id: string | undefined): void {
    this.menuOpen.set(false);
    if (openLegalPage(id)) return;
    if (id === 'account') {
      void this.router.navigate(['/platform/account', 'profile']);
    } else if (id === 'logout') {
      this.loginService.logout();
      void this.router.navigate(['/login']);
    }
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }
}
