import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AbstractControl, FormBuilder, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { LANGUAGES } from 'app/config/language.constants';
import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import { ThemeService } from 'app/core/theme/theme.service';
import { humanizeCode } from 'app/features/admin/models/user-admin.model';
import { CurrentEmployeeService } from 'app/features/employee/services/current-employee.service';
import { EmployeeService } from 'app/features/employee/services/employee.service';
import {
  AlertComponent,
  AvatarComponent,
  ButtonComponent,
  DialogComponent,
  FormFieldComponent,
  InputComponent,
  SelectComponent,
  SkeletonRowComponent,
  TabItem,
  TabsComponent,
  ToastService,
} from 'app/shared/ui';

import { AccountManagementService, Session } from './account-management.service';

type Tab = 'profile' | 'security' | 'preferences';

/** Minimum password length (the server's `Constants.PASSWORD_MIN_LENGTH`). */
const PASSWORD_MIN = 8;

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const a = group.get('newPassword')?.value;
  const b = group.get('confirmPassword')?.value;
  return a && b && a !== b ? { mismatch: true } : null;
}

/** "Chrome on macOS" and an icon from a user-agent string. */
function describeDevice(ua: string): { label: string; icon: string } {
  const browser = /Edg\//.test(ua)
    ? 'Edge'
    : /OPR\//.test(ua)
      ? 'Opera'
      : /Firefox\//.test(ua)
        ? 'Firefox'
        : /Chrome\//.test(ua)
          ? 'Chrome'
          : /Safari\//.test(ua)
            ? 'Safari'
            : /curl|Postman|insomnia/i.test(ua)
              ? 'API client'
              : 'Browser';
  const os = /iPhone|iPad/.test(ua)
    ? 'iOS'
    : /Android/.test(ua)
      ? 'Android'
      : /Mac OS X|Macintosh/.test(ua)
        ? 'macOS'
        : /Windows/.test(ua)
          ? 'Windows'
          : /Linux/.test(ua)
            ? 'Linux'
            : '';
  const mobile = /iPhone|Android.*Mobile/.test(ua);
  return { label: os ? `${browser} on ${os}` : browser, icon: mobile ? 'smartphone' : 'monitor' };
}

/**
 * Account — the signed-in person's own account (`/account/:tab`), replacing the legacy settings,
 * password and sessions pages:
 *
 * - Profile: photo (uploaded, as on the HR profile), name and email; sign-in name and roles shown
 *   read-only.
 * - Security: change password (strength meter and rules; at least 8 characters) and the devices
 *   kept signed in ("remember me"), recognised by browser and OS, each revocable — or all at once.
 * - Preferences: theme and density (remembered on this device) and language.
 */
@Component({
  selector: 'hum-account',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    AvatarComponent,
    ButtonComponent,
    DialogComponent,
    FormFieldComponent,
    InputComponent,
    SelectComponent,
    SkeletonRowComponent,
    TabsComponent,
  ],
  templateUrl: './account.component.html',
})
export default class AccountComponent {
  /** Route param; legacy paths (`settings`, `password`, `sessions`) map to their tab. */
  readonly section = input<string | undefined>();

  private readonly fb = inject(FormBuilder);
  private readonly accountService = inject(AccountService);
  private readonly api = inject(AccountManagementService);
  private readonly currentEmployee = inject(CurrentEmployeeService);
  private readonly employeeService = inject(EmployeeService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  protected readonly theme = inject(ThemeService);

  protected readonly account = this.accountService.trackCurrentAccount();
  /** Platform (console) accounts have no employee profile. */
  protected readonly isPlatform = computed(() => !!this.account() && this.accountService.isPlatformUser());
  protected readonly roleLabel = humanizeCode;
  protected readonly passwordMin = PASSWORD_MIN;

  protected readonly tab = signal<Tab>('profile');
  protected readonly tabs = computed<TabItem[]>(() => [
    { id: 'profile', label: this.t('humano.account.tab.profile'), icon: 'user' },
    { id: 'security', label: this.t('humano.account.tab.security'), icon: 'shield-check' },
    { id: 'preferences', label: this.t('humano.account.tab.preferences'), icon: 'settings' },
  ]);

  protected readonly fullName = computed(() => {
    const a = this.account();
    return a ? [a.firstName, a.lastName].filter(Boolean).join(' ') || a.login : '';
  });
  /** Roles, most significant first (skipping the implicit base user role). */
  protected readonly roles = computed(() => (this.account()?.authorities ?? []).filter(r => r !== 'ROLE_USER').sort());

  // ---- Profile
  protected readonly profileForm = this.fb.nonNullable.group({
    firstName: ['', Validators.maxLength(50)],
    lastName: ['', Validators.maxLength(50)],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });
  protected readonly savingProfile = signal(false);
  protected readonly photoBusy = signal(false);
  protected readonly canUploadPhoto = computed(() => !!this.currentEmployee.currentEmployeeId());

  // ---- Security
  protected readonly passwordForm = this.fb.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(PASSWORD_MIN), Validators.maxLength(100)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );
  private readonly newPassword = toSignal(this.passwordForm.controls.newPassword.valueChanges, { initialValue: '' });
  protected readonly rules = computed(() => {
    const p = this.newPassword();
    return [
      { key: 'length', ok: p.length >= PASSWORD_MIN },
      { key: 'case', ok: /[a-z]/.test(p) && /[A-Z]/.test(p) },
      { key: 'number', ok: /\d/.test(p) },
      { key: 'symbol', ok: /[^A-Za-z0-9]/.test(p) },
    ];
  });
  /** 0–4 from the rules met (only once the minimum length is reached). */
  protected readonly strength = computed(() => {
    const met = this.rules().filter(r => r.ok).length;
    return this.newPassword().length < PASSWORD_MIN ? Math.min(met, 1) : met;
  });
  protected readonly showPasswords = signal(false);
  protected readonly savingPassword = signal(false);

  protected readonly sessions = signal<(Session & { device: { label: string; icon: string } })[] | null>(null);
  protected readonly sessionsError = signal<string | null>(null);
  protected readonly confirmSignOutAll = signal(false);

  // ---- Preferences
  protected readonly languageOptions = LANGUAGES.map(key => ({
    value: key,
    label: new Intl.DisplayNames([key], { type: 'language' }).of(key) ?? key.toUpperCase(),
  }));

  constructor() {
    this.currentEmployee.resolve();
    effect(() => {
      const s = this.section();
      this.tab.set(s === 'security' || s === 'password' || s === 'sessions' ? 'security' : s === 'preferences' ? 'preferences' : 'profile');
    });
    effect(() => {
      const a = this.account();
      if (a && this.profileForm.pristine) {
        this.profileForm.reset({ firstName: a.firstName ?? '', lastName: a.lastName ?? '', email: a.email });
      }
    });
    effect(() => {
      if (this.tab() === 'security' && this.sessions() === null) this.loadSessions();
    });
  }

  protected setTab(id: string | undefined): void {
    if (id === 'profile' || id === 'security' || id === 'preferences') {
      this.tab.set(id);
      // Same page under the console (`/platform/account/…`) and the workspace (`/account/…`).
      const base = location.pathname.startsWith('/platform/') ? '/platform/account' : '/account';
      history.replaceState(history.state, '', `${base}/${id}`);
    }
  }

  protected invalid(form: 'profile' | 'password', control: string): boolean {
    const c = form === 'profile' ? this.profileForm.get(control) : this.passwordForm.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  // ---- Profile

  protected saveProfile(): void {
    const a = this.account();
    if (!a || this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    const v = this.profileForm.getRawValue();
    this.savingProfile.set(true);
    this.accountService
      .save({ ...a, firstName: v.firstName.trim() || null, lastName: v.lastName.trim() || null, email: v.email.trim() })
      .subscribe({
        next: () => {
          this.savingProfile.set(false);
          this.profileForm.markAsPristine();
          this.accountService.identity(true).subscribe();
          this.toast.success(this.t('humano.account.profileSaved'));
        },
        error: (err: unknown) => {
          this.savingProfile.set(false);
          this.toast.danger(normalizeHttpError(err));
        },
      });
  }

  protected onPhoto(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const id = this.currentEmployee.currentEmployeeId();
    if (!file || !id) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 2 * 1024 * 1024) {
      this.toast.danger(this.t('humano.employee360.photoInvalid'));
      return;
    }
    this.photoBusy.set(true);
    this.employeeService.uploadPhoto(id, file).subscribe({
      next: () => this.photoDone('humano.employee360.photoUpdated'),
      error: (err: unknown) => this.photoFailed(err),
    });
  }

  protected removePhoto(): void {
    const id = this.currentEmployee.currentEmployeeId();
    if (!id) return;
    this.photoBusy.set(true);
    this.employeeService.removePhoto(id).subscribe({
      next: () => this.photoDone('humano.employee360.photoRemoved'),
      error: (err: unknown) => this.photoFailed(err),
    });
  }

  // ---- Preferences

  protected setLanguage(langKey: string): void {
    const a = this.account();
    if (!a || !langKey || langKey === a.langKey) return;
    this.accountService.save({ ...a, langKey }).subscribe({
      next: () => {
        this.translate.use(langKey);
        this.accountService.identity(true).subscribe();
        this.toast.success(this.t('humano.account.preferencesSaved'));
      },
      error: (err: unknown) => this.toast.danger(normalizeHttpError(err)),
    });
  }

  // ---- Security

  protected changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const v = this.passwordForm.getRawValue();
    this.savingPassword.set(true);
    this.api.changePassword(v.currentPassword, v.newPassword).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.passwordForm.reset();
        this.toast.success(this.t('humano.account.passwordChanged'));
      },
      error: (err: unknown) => {
        this.savingPassword.set(false);
        this.toast.danger(this.isWrongPassword(err) ? this.t('humano.account.wrongPassword') : normalizeHttpError(err));
      },
    });
  }

  protected loadSessions(): void {
    this.sessionsError.set(null);
    this.api.sessions().subscribe({
      next: list =>
        this.sessions.set(
          [...list].sort((a, b) => b.tokenDate.localeCompare(a.tokenDate)).map(s => ({ ...s, device: describeDevice(s.userAgent ?? '') })),
        ),
      error: (err: unknown) => {
        this.sessionsError.set(normalizeHttpError(err));
        this.sessions.set([]);
      },
    });
  }

  protected revoke(series: string): void {
    this.api.invalidate(series).subscribe({
      next: () => {
        this.sessions.update(list => (list ?? []).filter(s => s.series !== series));
        this.toast.success(this.t('humano.account.signedOutDevice'));
      },
      error: (err: unknown) => this.toast.danger(normalizeHttpError(err)),
    });
  }

  protected signOutAll(): void {
    const list = this.sessions() ?? [];
    this.confirmSignOutAll.set(false);
    for (const s of list) this.revoke(s.series);
  }

  private photoDone(key: string): void {
    this.photoBusy.set(false);
    // The account and the employee record share the photo: refresh both so the shell updates.
    this.accountService.identity(true).subscribe();
    this.currentEmployee.resolve(true);
    this.toast.success(this.t(key));
  }

  private photoFailed(err: unknown): void {
    this.photoBusy.set(false);
    this.toast.danger(normalizeHttpError(err));
  }

  private isWrongPassword(err: unknown): boolean {
    const e = err as { status?: number; error?: { type?: string; message?: string } };
    return e?.status === 400 && /password/i.test(`${e.error?.type ?? ''} ${e.error?.message ?? ''}`);
  }

  private t(key: string): string {
    return String(this.translate.instant(key));
  }
}
