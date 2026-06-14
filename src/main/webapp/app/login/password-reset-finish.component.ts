import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AlertComponent, ButtonComponent, FormFieldComponent, InputComponent } from 'app/shared/ui';

import { AuthPublicService } from './auth-public.service';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('newPassword')!.value === group.get('confirmPassword')!.value ? null : { dontmatch: true };
}

/** Finish a password reset — `POST /api/account/reset-password/finish`. `key` from `?key=`. */
@Component({
  selector: 'hum-password-reset-finish',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, AlertComponent, ButtonComponent, InputComponent, FormFieldComponent],
  templateUrl: './password-reset-finish.component.html',
})
export default class PasswordResetFinishComponent {
  /** Reset key from `?key=` (bound via `withComponentInputBinding()`). */
  readonly key = input<string>();

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AuthPublicService);

  protected readonly loading = signal(false);
  protected readonly success = signal(false);
  protected readonly failed = signal(false);

  protected readonly form = this.fb.nonNullable.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(50)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  protected invalid(): boolean {
    const c = this.form.controls.newPassword;
    return c.invalid && (c.dirty || c.touched);
  }

  protected mismatch(): boolean {
    const confirm = this.form.controls.confirmPassword;
    return this.form.hasError('dontmatch') && (confirm.dirty || confirm.touched);
  }

  protected submit(): void {
    const key = this.key();
    if (this.form.invalid || !key) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.failed.set(false);
    this.api.finishPasswordReset(key, this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.success.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }
}
