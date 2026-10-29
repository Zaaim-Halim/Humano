import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  CheckboxComponent,
  DrawerComponent,
  IconButtonComponent,
  InputComponent,
  SelectComponent,
  SelectOption,
  SkeletonRowComponent,
  SwitchComponent,
  TabItem,
  TabsComponent,
  ToastService,
} from 'app/shared/ui';

import { AdminUser, ROLE_GROUPS, RolePermissions, humanizeCode } from '../../models/user-admin.model';
import { UserAdminService } from '../../services/user-admin.service';

/**
 * Users & access (administrators) — who can sign in and what they may do.
 *
 * - Users: accounts 20 at a time — search (login, email, name) and role filter run on the server,
 *   so it scales to thousands of users; each with its roles (readable names);
 *   switch an account off, or edit its roles in a side panel where roles are grouped by area and
 *   show how many permissions they grant. You cannot switch yourself off or drop your own admin role.
 * - Roles: each role with how many people hold it (counted in the database) and its permissions.
 */
@Component({
  selector: 'hum-access-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    CheckboxComponent,
    DrawerComponent,
    IconButtonComponent,
    InputComponent,
    SelectComponent,
    SkeletonRowComponent,
    SwitchComponent,
    TabsComponent,
  ],
  templateUrl: './access-settings.component.html',
})
export class AccessSettingsComponent {
  private readonly userAdmin = inject(UserAdminService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly me = inject(AccountService).trackCurrentAccount();

  protected readonly label = humanizeCode;
  protected readonly tab = signal<'users' | 'roles'>('users');
  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'users', label: this.t('humano.settings.access.users'), icon: 'users', count: this.allUsers() ?? undefined },
    { id: 'roles', label: this.t('humano.settings.access.roles'), icon: 'shield-check', count: this.roles().length },
  ]);

  // Users: one server page at a time, searched and filtered on the server.
  protected readonly pageSize = 20;
  protected readonly users = signal<AdminUser[] | null>(null);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly loadingPage = signal(false);
  protected readonly roles = signal<RolePermissions[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  private readonly debouncedQuery = toSignal(toObservable(this.query).pipe(debounceTime(300), distinctUntilChanged()), {
    initialValue: '',
  });
  protected readonly roleFilter = signal('');
  protected readonly lastPage = computed(() => Math.max(0, Math.ceil(this.total() / this.pageSize) - 1));
  protected readonly range = computed(() => {
    const t = this.total();
    if (!t) return { from: 0, to: 0, total: 0 };
    const from = this.page() * this.pageSize + 1;
    return { from, to: Math.min(t, from + (this.users()?.length ?? 0) - 1), total: t };
  });

  private readonly permissionCount = computed(() => new Map(this.roles().map(r => [r.name, r.permissions.length])));
  protected readonly roleGroups = computed(() => {
    const known = new Set(this.roles().map(r => r.name));
    const grouped = new Set(ROLE_GROUPS.flatMap(g => g.roles));
    const groups = ROLE_GROUPS.map(g => ({ ...g, roles: g.roles.filter(r => known.has(r)) }));
    const other = [...known].filter(r => !grouped.has(r));
    return other.length ? [...groups, { id: 'other', icon: 'tag', roles: other }] : groups;
  });
  protected readonly roleOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.t('humano.settings.access.allRoles') },
    ...this.roles().map(r => ({ value: r.name, label: humanizeCode(r.name) })),
  ]);
  /** Users per role, counted in the database. */
  protected readonly holders = computed(() => new Map(this.roles().map(r => [r.name, r.userCount])));
  /** Total accounts (unfiltered), for the tab badge. */
  protected readonly allUsers = signal<number | null>(null);
  protected readonly expandedRole = signal<string | null>(null);

  // Editor
  protected readonly editing = signal<AdminUser | null>(null);
  protected readonly draftRoles = signal<Set<string>>(new Set());
  protected readonly draftActive = signal(true);
  protected readonly saving = signal(false);
  protected readonly draftPermissionCount = computed(() => {
    const perms = new Set<string>();
    for (const r of this.roles()) if (this.draftRoles().has(r.name)) r.permissions.forEach(p => perms.add(p));
    return perms.size;
  });

  constructor() {
    this.load();
    // A new search or role filter starts again from the first page.
    effect(() => {
      this.debouncedQuery();
      this.roleFilter();
      untracked(() => this.page.set(0));
    });
    effect(() => {
      const params = { page: this.page(), size: this.pageSize, q: this.debouncedQuery().trim(), role: this.roleFilter() };
      untracked(() => this.loadPage(params));
    });
  }

  protected load(): void {
    this.error.set(null);
    this.userAdmin.roles().subscribe({
      next: roles => this.roles.set(roles),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
    this.userAdmin.page({ page: 0, size: 1 }).subscribe({ next: r => this.allUsers.set(r.total), error: () => undefined });
  }

  protected goto(page: number): void {
    this.page.set(Math.min(Math.max(0, page), this.lastPage()));
  }

  protected setTab(id: string | undefined): void {
    if (id === 'users' || id === 'roles') this.tab.set(id);
  }

  protected name(u: AdminUser): string {
    return [u.firstName, u.lastName].filter(Boolean).join(' ') || u.login;
  }

  protected isSelf(u: AdminUser): boolean {
    return u.login === this.me()?.login;
  }

  protected permissions(role: string): number {
    return this.permissionCount().get(role) ?? 0;
  }

  /** Roles to show as chips: the most powerful first. */
  protected topRoles(u: AdminUser): string[] {
    return [...u.authorities].sort((a, b) => this.permissions(b) - this.permissions(a));
  }

  protected openEdit(u: AdminUser): void {
    this.editing.set(u);
    this.draftRoles.set(new Set(u.authorities));
    this.draftActive.set(u.activated);
  }

  protected toggleRole(role: string, on: boolean): void {
    const u = this.editing();
    if (u && this.isSelf(u) && role === 'ROLE_ADMIN' && !on) return; // never lock yourself out
    this.draftRoles.update(s => {
      const next = new Set(s);
      if (on) next.add(role);
      else next.delete(role);
      return next;
    });
  }

  protected save(): void {
    const u = this.editing();
    if (!u) return;
    this.saving.set(true);
    this.persist(u, { activated: this.isSelf(u) ? true : this.draftActive(), authorities: [...this.draftRoles()] }, () =>
      this.editing.set(null),
    );
  }

  protected setActive(u: AdminUser, activated: boolean): void {
    if (this.isSelf(u)) return;
    this.persist(u, { activated, authorities: u.authorities });
  }

  /** Roles are shown first by their permission weight; the full list explains each. */
  protected permissionsOf(role: string): string[] {
    return this.roles().find(r => r.name === role)?.permissions ?? [];
  }

  private loadPage(params: { page: number; size: number; q: string; role: string }): void {
    this.loadingPage.set(true);
    this.userAdmin.page(params).subscribe({
      next: ({ users, total }) => {
        this.users.set(users);
        this.total.set(total);
        this.loadingPage.set(false);
      },
      error: (err: unknown) => {
        this.loadingPage.set(false);
        this.error.set(normalizeHttpError(err));
      },
    });
  }

  private persist(u: AdminUser, change: { activated: boolean; authorities: string[] }, done?: () => void): void {
    this.userAdmin
      .update({
        id: u.id,
        login: u.login,
        firstName: u.firstName,
        lastName: u.lastName,
        email: u.email,
        imageUrl: u.imageUrl,
        langKey: u.langKey,
        activated: change.activated,
        authorities: change.authorities,
      })
      .subscribe({
        next: saved => {
          this.saving.set(false);
          this.users.update(list => (list ?? []).map(x => (x.id === saved.id ? saved : x)));
          // Role changes move people between roles: refresh the per-role counts.
          this.userAdmin.roles().subscribe(roles => this.roles.set(roles));
          this.toast.success(this.t('humano.settings.saved'));
          done?.();
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.toast.danger(normalizeHttpError(err));
        },
      });
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }
}
