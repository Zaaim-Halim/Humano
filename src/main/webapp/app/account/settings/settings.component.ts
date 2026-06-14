import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { LANGUAGES } from 'app/config/language.constants';
import { AccountService } from 'app/core/auth/account.service';
import { ButtonComponent, FormFieldComponent, InputComponent, PageHeaderComponent, SelectComponent, ToastService } from 'app/shared/ui';
import { stripHtml } from 'app/shared/util/strip-html';

/**
 * Account settings — edit own profile (`POST /api/account` via `AccountService`),
 * then refresh identity so the shell reflects the change. In-shell, authenticated.
 */
@Component({
  selector: 'hum-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, TranslatePipe, PageHeaderComponent, ButtonComponent, InputComponent, SelectComponent, FormFieldComponent],
  template: `
    <div class="hum-page" style="max-width:560px">
      <hum-page-header [title]="title()" [subtitle]="'settings.form.button' | translate" />

      <form [formGroup]="form" (ngSubmit)="save()" novalidate style="display:grid;gap:var(--space-4)">
        <hum-form-field [label]="'settings.form.firstname' | translate" controlId="settings-firstname">
          <hum-input
            inputId="settings-firstname"
            formControlName="firstName"
            [placeholder]="'settings.form.firstname.placeholder' | translate"
          />
        </hum-form-field>

        <hum-form-field [label]="'settings.form.lastname' | translate" controlId="settings-lastname">
          <hum-input
            inputId="settings-lastname"
            formControlName="lastName"
            [placeholder]="'settings.form.lastname.placeholder' | translate"
          />
        </hum-form-field>

        <hum-form-field
          [label]="'global.form.email.label' | translate"
          controlId="settings-email"
          [error]="invalid('email') ? ('global.messages.validate.email.invalid' | translate) : undefined"
        >
          <hum-input
            inputId="settings-email"
            type="email"
            formControlName="email"
            [placeholder]="'global.form.email.placeholder' | translate"
            [invalid]="invalid('email')"
          />
        </hum-form-field>

        <hum-form-field [label]="'settings.form.language' | translate" controlId="settings-lang">
          <hum-select formControlName="langKey" [options]="languageOptions" />
        </hum-form-field>

        <div>
          <hum-button type="submit" variant="primary" [loading]="saving()" [disabled]="form.invalid">{{
            'settings.form.button' | translate
          }}</hum-button>
        </div>
      </form>
    </div>
  `,
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
    langKey: ['en'],
  });

  protected readonly title = signal(this.translate.instant('settings.title', { username: '' }));

  constructor() {
    // Hydrate the form (and title) once the account signal resolves.
    effect(() => {
      const a = this.account();
      if (a) {
        this.form.patchValue({ firstName: a.firstName ?? '', lastName: a.lastName ?? '', email: a.email, langKey: a.langKey });
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
    const { firstName, lastName, email, langKey } = this.form.getRawValue();
    this.accountService.save({ ...current, firstName, lastName, email, langKey }).subscribe({
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
