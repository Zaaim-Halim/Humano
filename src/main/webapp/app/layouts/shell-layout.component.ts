import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { merge } from 'rxjs';

import { Authority } from 'app/config/authority.constants';
import { AccountService } from 'app/core/auth/account.service';
import { ThemeService } from 'app/core/theme/theme.service';
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
  ShellUser,
} from 'app/shared/ui';

import { SHELL_NAV } from './shell-nav';

/**
 * ShellLayoutComponent — the authenticated app frame and the real consumer of
 * `AppShell`. Sources identity/roles from `AccountService`, builds the
 * role-filtered nav reactively, composes the top-bar actions (notifications,
 * theme toggle, user menu) into the shell's slot, wires the search trigger to
 * the ⌘K palette, and hosts feature routes in `<router-outlet>`.
 *
 * The sidebar nav and the command palette both derive from one filtered source,
 * so a route can never drift between them. Labels are i18n keys resolved here
 * (the design-system components take already-translated strings); resolution is
 * reactive — `i18nTick` re-runs the label computeds on language/translation load.
 */
@Component({
  selector: 'hum-shell-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, TranslatePipe, AppShellComponent, CommandPaletteComponent, IconButtonComponent, MenuComponent, AvatarComponent],
  template: `
    <!-- TODO: wire [tenant] from CurrentTenantService (GET /api/tenant/me); omitted until data-wiring in Phase 7. -->
    <hum-app-shell
      [nav]="nav()"
      [user]="user()"
      [searchPlaceholder]="'humano.shell.search' | translate"
      (searchClick)="palette.openPalette()"
    >
      <div hum-topbar-actions style="display:flex;align-items:center;gap:var(--space-1)">
        <hum-icon-button icon="life-buoy" [label]="'humano.shell.help' | translate" />
        <!-- TODO: backend endpoint missing — notifications feed/count; affordance only for now. -->
        <hum-icon-button icon="bell" [label]="'humano.shell.notifications' | translate" />
        <hum-icon-button
          [icon]="theme.theme() === 'dark' ? 'sun' : 'moon'"
          [label]="'humano.shell.toggleTheme' | translate"
          (click)="theme.toggleTheme()"
        />

        @if (user(); as u) {
          <div style="position:relative" (keydown.escape)="userMenuOpen.set(false)">
            <button
              type="button"
              class="hum-btn hum-btn--icon hum-btn--ghost"
              [attr.aria-label]="'humano.shell.accountMenu' | translate: { name: u.name }"
              aria-haspopup="menu"
              [attr.aria-expanded]="userMenuOpen()"
              (click)="userMenuOpen.set(!userMenuOpen())"
            >
              <hum-avatar [name]="u.name" [src]="u.src ?? null" size="xs" />
            </button>
            @if (userMenuOpen()) {
              <button
                type="button"
                aria-hidden="true"
                tabindex="-1"
                (click)="userMenuOpen.set(false)"
                style="position:fixed;inset:0;z-index:55;background:transparent;border:0;cursor:default"
              ></button>
              <div style="position:absolute;right:0;top:calc(100% + var(--space-1_5));z-index:60">
                <hum-menu [items]="userMenu()" (selected)="onUserMenuSelect($event)" />
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
export default class ShellLayoutComponent {
  protected readonly theme = inject(ThemeService);
  private readonly accountService = inject(AccountService);
  private readonly loginService = inject(LoginService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);

  private readonly account = this.accountService.trackCurrentAccount();
  protected readonly userMenuOpen = signal(false);

  /** Re-runs the label computeds when the language or loaded translations change. */
  private readonly i18nTick = toSignal(merge(this.translate.onLangChange, this.translate.onTranslationChange), { initialValue: null });

  protected readonly user = computed<ShellUser | undefined>(() => {
    const a = this.account();
    if (!a) return undefined;
    const name = [a.firstName, a.lastName].filter(Boolean).join(' ') || a.login;
    return {
      name,
      role: this.t(a.authorities.includes(Authority.ADMIN) ? 'humano.shell.roles.admin' : 'humano.shell.roles.employee'),
      src: a.imageUrl,
    };
  });

  protected readonly userMenu = computed<MenuItem[]>(() => [
    { heading: this.t('humano.shell.account') },
    { id: 'profile', label: this.t('humano.shell.profile'), icon: 'user' },
    { id: 'settings', label: this.t('humano.shell.settings'), icon: 'settings' },
    { separator: true },
    { id: 'logout', label: this.t('humano.shell.logout'), icon: 'log-out', danger: true },
  ]);

  /** Role-filtered nav (sidebar + palette both derive from this). */
  private readonly visible = computed(() => {
    const a = this.account();
    const allowed = (roles?: string[]): boolean => !roles?.length || (!!a && roles.some(r => a.authorities.includes(r)));
    return SHELL_NAV.filter(g => allowed(g.roles))
      .map(g => ({ ...g, items: g.items.filter(it => allowed(it.roles)) }))
      .filter(g => g.items.length > 0);
  });

  protected readonly nav = computed<NavGroup[]>(() =>
    this.visible().map(g => ({
      heading: this.t(g.headingKey),
      items: g.items.map(it => ({ id: it.id, label: this.t(it.labelKey), icon: it.icon, badge: it.badge, link: it.link })),
    })),
  );

  protected readonly commands = computed<Command[]>(() =>
    this.visible().flatMap(g =>
      g.items.map(it => ({ id: it.id, label: this.t(it.labelKey), icon: it.icon, group: this.t(g.headingKey), keywords: it.keywords })),
    ),
  );

  private readonly linkById = computed(() => new Map(this.visible().flatMap(g => g.items.map(it => [it.id, it.link] as const))));

  protected onCommand(id: string): void {
    const link = this.linkById().get(id);
    if (link) {
      void this.router.navigateByUrl(link);
    }
  }

  protected onUserMenuSelect(id: string | undefined): void {
    this.userMenuOpen.set(false);
    switch (id) {
      case 'logout':
        this.loginService.logout();
        void this.router.navigate(['/login']);
        break;
      case 'profile':
      case 'settings':
        // TODO: account/settings surfaces land in Phase 5 (auth/account re-skin).
        break;
    }
  }

  /** Reactive translation: reading `i18nTick` ties the caller's computed to language changes. */
  private t(key: string): string {
    this.i18nTick();
    return this.translate.instant(key);
  }
}
