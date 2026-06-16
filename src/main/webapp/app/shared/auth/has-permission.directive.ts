import { Directive, Input, OnDestroy, OnInit, TemplateRef, ViewContainerRef, inject } from '@angular/core';
import { Subscription } from 'rxjs';

import { AccountService } from 'app/core/auth/account.service';

/**
 * Structural directive that renders its host element only when the current user
 * holds (any of) the given permission(s). Mirrors `AccountService.hasAnyPermission`
 * and re-evaluates whenever the authentication state changes.
 *
 * Usage:
 * ```html
 * <button *humHasPermission="Permission.APPROVE_PAYROLL">Approve</button>
 * <a *humHasPermission="[Permission.VIEW_PAYSLIPS, Permission.GENERATE_PAYSLIPS]">Payslips</a>
 * ```
 */
@Directive({
  selector: '[humHasPermission]',
})
export class HasPermissionDirective implements OnInit, OnDestroy {
  private permissions: string[] = [];
  private hasView = false;
  private authSubscription?: Subscription;

  private readonly templateRef = inject<TemplateRef<unknown>>(TemplateRef);
  private readonly viewContainer = inject(ViewContainerRef);
  private readonly accountService = inject(AccountService);

  @Input({ required: true }) set humHasPermission(value: string | string[]) {
    this.permissions = Array.isArray(value) ? value : [value];
    this.updateView();
  }

  ngOnInit(): void {
    this.authSubscription = this.accountService.getAuthenticationState().subscribe(() => this.updateView());
  }

  ngOnDestroy(): void {
    this.authSubscription?.unsubscribe();
  }

  private updateView(): void {
    const allowed = this.accountService.hasAnyPermission(this.permissions);
    if (allowed && !this.hasView) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.hasView = true;
    } else if (!allowed && this.hasView) {
      this.viewContainer.clear();
      this.hasView = false;
    }
  }
}
