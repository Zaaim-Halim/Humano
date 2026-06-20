import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { LANGUAGES } from 'app/config/language.constants';
import { AccountService } from 'app/core/auth/account.service';
import {
  AvatarComponent,
  ButtonComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  SelectComponent,
  ToastService,
} from 'app/shared/ui';
import { stripHtml } from 'app/shared/util/strip-html';

/**
 * Account settings — edit own profile (`POST /api/account` via `AccountService`),
 * then refresh identity so the shell reflects the change. In-shell, authenticated.
 */
@Component({
  selector: 'hum-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    PageHeaderComponent,
    ButtonComponent,
    InputComponent,
    SelectComponent,
    FormFieldComponent,
    AvatarComponent,
  ],
  templateUrl: './settings.component.html',
})
export default class SettingsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly accountService = inject(AccountService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  private readonly account = this.accountService.trackCurrentAccount();
  protected readonly saving = signal(false);
  protected readonly languageOptions = LANGUAGES.map(key => ({ value: key, label: key.toUpperCase() }));

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.maxLength(50)]],
    lastName: ['', [Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email, Validators.minLength(5), Validators.maxLength(254)]],
    imageUrl: ['', [Validators.maxLength(256)]],
    langKey: ['en'],
  });

  // Live preview of the avatar as the URL/name fields change (image when set, else initials).
  private readonly liveValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  protected readonly previewSrc = computed(() => {
    const url = this.liveValue().imageUrl?.trim();
    if (!url) return null;
    return url;
  });
  protected readonly previewName = computed(() => {
    const v = this.liveValue();
    const full = [v.firstName, v.lastName].filter(Boolean).join(' ');
    return full !== '' ? full : (this.account()?.login ?? '');
  });

  protected readonly title = signal(this.translate.instant('settings.title', { username: '' }));

  constructor() {
    // Hydrate the form (and title) once the account signal resolves.
    effect(() => {
      const a = this.account();
      if (a) {
        this.form.patchValue({
          firstName: a.firstName ?? '',
          lastName: a.lastName ?? '',
          email: a.email,
          imageUrl: a.imageUrl ?? '',
          langKey: a.langKey,
        });
        this.title.set(this.translate.instant('settings.title', { username: a.login }));
      }
    });
  }

  protected invalid(control: 'email'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.dirty || c.touched);
  }

  /** Translate then flatten any `<strong>` markup for plain-text toasts. */
  private msg(key: string): string {
    return stripHtml(this.translate.instant(key));
  }

  protected save(): void {
    const current = this.account();
    if (this.form.invalid || !current) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const { firstName, lastName, email, imageUrl, langKey } = this.form.getRawValue();
    const trimmedImage = imageUrl.trim();
    this.accountService
      .save({ ...current, firstName, lastName, email, imageUrl: trimmedImage === '' ? null : trimmedImage, langKey })
      .subscribe({
        next: () => {
          this.accountService.identity(true).subscribe();
          if (langKey) {
            this.translate.use(langKey);
          }
          this.toast.success(this.msg('settings.messages.success'));
          this.saving.set(false);
        },
        error: () => {
          this.toast.danger(this.msg('settings.messages.error.fail'));
          this.saving.set(false);
        },
      });
  }
}
