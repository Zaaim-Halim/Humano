import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { AlertComponent, ButtonComponent, CheckboxComponent, FormFieldComponent, InputComponent } from 'app/shared/ui';
import { TenantContextService } from 'app/core/tenant/tenant-context.service';

import { LoginService } from './login.service';

/**
 * Sign-in screen. Public route (outside the shell). Delegates the transport to
 * `LoginService` (form-encoded `api/authentication` via `AuthServerProvider`)
 * and lets `AccountService.navigateToStoredUrl()` handle the post-login
 * redirect to the intended URL; only falls back to home when none was stored.
 */
@Component({
  selector: 'hum-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    ButtonComponent,
    InputComponent,
    CheckboxComponent,
    FormFieldComponent,
  ],
  templateUrl: './login.component.html',
})
export default class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly loginService = inject(LoginService);
  private readonly router = inject(Router);
  private readonly tenantContext = inject(TenantContextService);

  protected readonly loading = signal(false);
  protected readonly authError = signal(false);
  protected readonly showPassword = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    // Organization = the tenant the user belongs to. Interim until production resolves the tenant from
    // the subdomain; sent as the X-Tenant-ID header so the backend knows which tenant DB to authenticate against.
    organization: [this.tenantContext.tenantId() ?? '', [Validators.required]],
    username: ['', [Validators.required]],
    password: ['', [Validators.required]],
    rememberMe: [false],
  });

  protected invalid(control: 'organization' | 'username' | 'password'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.dirty || c.touched);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.authError.set(false);
    const { organization, username, password, rememberMe } = this.form.getRawValue();
    // Pin the tenant before the request so the interceptor attaches X-Tenant-ID to the login POST and
    // the follow-up account fetch.
    this.tenantContext.setTenant(organization);
    this.loginService.login({ username, password, rememberMe }).subscribe({
      next: () => {
        this.loading.set(false);
        // AccountService.navigateToStoredUrl() redirects to the intended URL when
        // the guard stored one; otherwise land on home.
        if (!this.router.getCurrentNavigation()) {
          void this.router.navigate(['']);
        }
      },
      error: () => {
        this.loading.set(false);
        this.authError.set(true);
      },
    });
  }
}
