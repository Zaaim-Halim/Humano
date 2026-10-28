import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { LEGAL_VERSION } from 'app/features/legal/legal-info';
import { MeBillingService, TermsStatus } from 'app/features/platform/services/me-billing.service';
import { AlertComponent, ButtonComponent, ToastService } from 'app/shared/ui';

/**
 * Asks a billing admin to accept the Terms of Service and DPA for the company when they are not the
 * current version — changed since they were accepted, or never accepted (workspaces from before
 * acceptance was recorded). Never blocks anything: under the Terms, changes apply from the next renewal
 * and a customer who disagrees may cancel. Nothing shows for other people, demos, or once accepted.
 */
@Component({
  selector: 'hum-terms-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, AlertComponent, ButtonComponent],
  template: `
    @if (status(); as s) {
      @if (s.applies && !s.upToDate) {
        <hum-alert tone="info" style="margin-bottom: var(--space-4)">
          {{ (s.acceptedVersion ? 'humano.terms.updated' : 'humano.terms.notAccepted') | translate }}
          <a href="/legal/terms" target="_blank" rel="noopener" style="margin-left: var(--space-2); font-weight: 600">{{
            'humano.terms.review' | translate
          }}</a>
          <hum-button
            size="sm"
            variant="secondary"
            icon="check"
            [loading]="accepting()"
            (click)="accept()"
            style="margin-left: var(--space-3)"
            >{{ 'humano.terms.accept' | translate }}</hum-button
          >
        </hum-alert>
      }
    }
  `,
})
export class TermsBannerComponent {
  private readonly meBilling = inject(MeBillingService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly account = inject(AccountService).trackCurrentAccount();

  private readonly canManage = computed(() => !!this.account()?.permissions?.includes(Permission.MANAGE_SUBSCRIPTIONS));
  protected readonly status = signal<TermsStatus | null>(null);
  protected readonly accepting = signal(false);

  private readonly load = effect(() => {
    if (this.canManage()) {
      untracked(() => this.meBilling.terms().subscribe({ next: s => this.status.set(s), error: () => this.status.set(null) }));
    }
  });

  protected accept(): void {
    this.accepting.set(true);
    this.meBilling.acceptTerms(LEGAL_VERSION).subscribe({
      next: s => {
        this.accepting.set(false);
        this.status.set(s);
        this.toast.show({ tone: 'success', title: this.translate.instant('humano.terms.accepted') });
      },
      error: (err: HttpErrorResponse) => {
        this.accepting.set(false);
        const outdated = (err.error as { message?: string } | null)?.message === 'error.termsoutdated';
        this.toast.show({ tone: 'danger', title: this.translate.instant(outdated ? 'humano.terms.outdated' : 'humano.terms.failed') });
      },
    });
  }
}
