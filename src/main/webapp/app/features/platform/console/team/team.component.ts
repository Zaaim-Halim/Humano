import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';
import { Observable } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import {
  AlertComponent,
  AvatarComponent,
  BadgeComponent,
  ButtonComponent,
  DialogComponent,
  DrawerComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  SkeletonRowComponent,
  SwitchComponent,
  ToastService,
} from 'app/shared/ui';

import { PLATFORM_ROLE_OPTIONS, PlatformRole, TeamMember } from '../../models/platform.model';
import { PlatformConsoleService } from '../../services/platform-console.service';

/**
 * Owner console — the platform's own staff. Everyone on the team can see it; owners invite
 * people (they get an email to set a password), change roles (owner: everything incl. billing
 * changes, deprovisioning and the team; admin: run customers and view billing), switch access off,
 * resend invitations and remove people. The server keeps at least one active owner and stops
 * anyone from changing their own access.
 */
@Component({
  selector: 'hum-console-team',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    DialogComponent,
    DrawerComponent,
    FormFieldComponent,
    InputComponent,
    PageHeaderComponent,
    SelectComponent,
    SkeletonRowComponent,
    SwitchComponent,
  ],
  templateUrl: './team.component.html',
})
export default class ConsoleTeamComponent {
  private readonly api = inject(PlatformConsoleService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService).trackCurrentAccount();

  protected readonly members = signal<TeamMember[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly canManage = computed(() => !!this.account()?.permissions?.includes(Permission.MANAGE_PLATFORM_USERS));

  protected readonly roleOptions = computed(() =>
    PLATFORM_ROLE_OPTIONS.map(r => ({ value: r, label: this.t(`humano.console.roles.${r}`) })),
  );
  protected readonly invite = signal<{ email: string; firstName: string; lastName: string; role: PlatformRole } | null>(null);
  protected readonly removing = signal<TeamMember | null>(null);

  protected readonly counts = computed(() => {
    const list = this.members() ?? [];
    return {
      owners: list.filter(m => m.role === 'ROLE_PLATFORM_OWNER' && m.activated).length,
      admins: list.filter(m => m.role === 'ROLE_PLATFORM_ADMIN' && m.activated).length,
      pending: list.filter(m => m.invitationPending).length,
    };
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.api.team().subscribe({
      next: list => this.members.set(list),
      error: (err: unknown) => this.error.set(normalizeHttpError(err)),
    });
  }

  protected name(m: TeamMember): string {
    return [m.firstName, m.lastName].filter(Boolean).join(' ') || m.email;
  }

  protected isSelf(m: TeamMember): boolean {
    const login = this.account()?.login?.toLowerCase();
    return !!login && m.email?.toLowerCase() === login;
  }

  protected openInvite(): void {
    this.invite.set({ email: '', firstName: '', lastName: '', role: 'ROLE_PLATFORM_ADMIN' });
  }

  protected patchInvite(patch: Partial<{ email: string; firstName: string; lastName: string; role: PlatformRole }>): void {
    this.invite.update(d => (d ? { ...d, ...patch } : d));
  }

  protected inviteValid(): boolean {
    const d = this.invite();
    return !!d && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(d.email.trim());
  }

  protected sendInvite(): void {
    const d = this.invite();
    if (!d || !this.inviteValid()) return;
    this.run(
      this.api.invite({
        email: d.email.trim(),
        firstName: d.firstName.trim() || undefined,
        lastName: d.lastName.trim() || undefined,
        role: d.role,
      }),
      'humano.console.team.invitedToast',
      () => this.invite.set(null),
    );
  }

  protected setRole(m: TeamMember, role: PlatformRole): void {
    if (role === m.role) return;
    this.run(this.api.updateMember(m.id, { role }), 'humano.console.team.roleChanged');
  }

  protected setActive(m: TeamMember, activated: boolean): void {
    this.run(this.api.updateMember(m.id, { activated }), activated ? 'humano.console.team.enabled' : 'humano.console.team.disabled');
  }

  protected resend(m: TeamMember): void {
    this.run(this.api.resendInvite(m.id), 'humano.console.team.resent');
  }

  protected remove(): void {
    const m = this.removing();
    if (!m) return;
    this.run(this.api.removeMember(m.id), 'humano.console.team.removed', () => this.removing.set(null));
  }

  private run(call: Observable<unknown>, doneKey: string, after?: () => void): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        after?.();
        this.toast.success(this.t(doneKey));
        this.load();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toast.danger(normalizeHttpError(err));
        this.load();
      },
    });
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }
}
