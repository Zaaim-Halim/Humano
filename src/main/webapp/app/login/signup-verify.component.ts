import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { TenantContextService } from 'app/core/tenant/tenant-context.service';
import { MarketingService, SignupView } from 'app/features/marketing/marketing.service';
import { AlertComponent, ButtonComponent, FormFieldComponent, InputComponent, ProgressComponent } from 'app/shared/ui';

import { passwordsMatch } from './password-reset-finish.component';
import { PasswordStrengthComponent } from './password-strength.component';
import { signupErrorKey } from './signup.component';

const POLL_MS = 2000;

/**
 * The emailed sign-up link (`/signup/verify?token=`). Opening it confirms the email; the person
 * then chooses their name and password, which starts the workspace setup. The page follows the
 * setup until the workspace is ready, then sends them to sign in with the workspace ID filled in.
 * A failed setup can be retried from here — it resumes where it stopped.
 */
@Component({
  selector: 'hum-signup-verify',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hum-auth-screen' },
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    ButtonComponent,
    FormFieldComponent,
    InputComponent,
    ProgressComponent,
    PasswordStrengthComponent,
  ],
  templateUrl: './signup-verify.component.html',
})
export default class SignupVerifyComponent implements OnInit {
  /** From `?token=` (bound via `withComponentInputBinding()`). */
  readonly token = input<string>();

  private readonly fb = inject(FormBuilder);
  private readonly marketing = inject(MarketingService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly router = inject(Router);

  protected readonly view = signal<SignupView | null>(null);
  /** i18n key when the link cannot be used (invalid, expired, address taken). */
  protected readonly linkError = signal<string | null>(null);
  protected readonly submitError = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly showPassword = signal(false);
  /** Fake-but-honest progress while the setup runs: creeps towards 90% until the server says ready. */
  protected readonly progress = signal(8);

  private timer: ReturnType<typeof setTimeout> | null = null;

  protected readonly form = this.fb.nonNullable.group(
    {
      firstName: ['', [Validators.required, Validators.maxLength(50)]],
      lastName: ['', [Validators.required, Validators.maxLength(50)]],
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  protected readonly passwordValue = toSignal(this.form.controls.newPassword.valueChanges, { initialValue: '' });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  ngOnInit(): void {
    const token = this.token();
    if (!token) {
      this.linkError.set('register.errors.invalidlink');
      return;
    }
    this.marketing.signupLink(token).subscribe({
      next: v => this.show(v),
      error: (err: HttpErrorResponse) => this.linkError.set(signupErrorKey(err)),
    });
  }

  protected invalid(control: 'firstName' | 'lastName' | 'newPassword'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.dirty || c.touched);
  }

  protected mismatch(): boolean {
    const confirm = this.form.controls.confirmPassword;
    return this.form.hasError('dontmatch') && (confirm.dirty || confirm.touched);
  }

  protected submit(): void {
    const token = this.token();
    if (this.form.invalid || !token) {
      this.form.markAllAsTouched();
      return;
    }
    const { firstName, lastName, newPassword: password } = this.form.getRawValue();
    this.loading.set(true);
    this.submitError.set(null);
    this.marketing.completeSignup({ token, firstName: firstName.trim(), lastName: lastName.trim(), password }).subscribe({
      next: v => {
        this.loading.set(false);
        this.progress.set(8);
        this.show(v);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.submitError.set(signupErrorKey(err));
      },
    });
  }

  /** Off to sign in, with the workspace and email already filled in. */
  protected signIn(): void {
    const v = this.view();
    if (!v) return;
    this.tenantContext.setTenant(v.subdomain);
    // The email travels in navigation state, not the URL (keeps it out of history and logs).
    void this.router.navigate(['/login'], { state: { email: v.email } });
  }

  private show(v: SignupView): void {
    this.view.set(v);
    this.stopPolling();
    if (v.stage === 'SETTING_UP') {
      this.timer = setTimeout(() => this.poll(), POLL_MS);
    } else if (v.stage === 'READY') {
      this.progress.set(100);
    }
  }

  private poll(): void {
    const token = this.token();
    if (!token) return;
    this.progress.update(p => Math.min(90, p + (90 - p) * 0.15));
    this.marketing.signupLink(token).subscribe({
      next: v => this.show(v),
      // A blip while polling: keep trying.
      error: () => (this.timer = setTimeout(() => this.poll(), POLL_MS * 2)),
    });
  }

  private stopPolling(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
