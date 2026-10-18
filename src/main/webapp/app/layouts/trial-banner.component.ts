import { ChangeDetectionStrategy, Component, computed, effect, inject, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { SubscriptionStatus } from 'app/features/platform/models/enums/subscription-status.enum';
import { MeBillingService } from 'app/features/platform/services/me-billing.service';
import { AlertComponent } from 'app/shared/ui';

const DAY_MS = 86_400_000;

/**
 * The trial line above every page of a customer workspace:
 * - during the trial, billing admins see the days left and a link to choose a plan;
 * - once a trial has ended without a plan, everyone sees that the workspace is read-only (and
 *   until when), billing admins with the link to choose a plan.
 * Nothing shows for paying customers or demo workspaces (no subscription).
 */
@Component({
  selector: 'hum-trial-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, TranslatePipe, AlertComponent],
  template: `
    @if (state(); as s) {
      <hum-alert [tone]="s.kind === 'readOnly' ? 'warning' : s.daysLeft <= 3 ? 'warning' : 'info'" style="margin-bottom: var(--space-4)">
        @if (s.kind === 'trial') {
          {{ (s.daysLeft === 1 ? 'humano.trial.lastDay' : 'humano.trial.daysLeft') | translate: { n: s.daysLeft } }}
        } @else {
          {{ 'humano.trial.readOnly' | translate: { date: (s.until | date: 'longDate') } }}
        }
        @if (canManage()) {
          <a routerLink="/settings/billing" style="margin-left: var(--space-2); font-weight: 600">{{
            'humano.trial.choosePlan' | translate
          }}</a>
        } @else if (s.kind === 'readOnly') {
          {{ 'humano.trial.askAdmin' | translate }}
        }
      </hum-alert>
    }
  `,
})
export class TrialBannerComponent {
  private readonly meBilling = inject(MeBillingService);
  private readonly account = inject(AccountService).trackCurrentAccount();

  protected readonly canManage = computed(() => !!this.account()?.permissions?.includes(Permission.MANAGE_SUBSCRIPTIONS));

  private readonly load = effect(() => {
    if (this.account() && this.meBilling.current() === undefined) {
      untracked(() => this.meBilling.currentSubscription().subscribe({ error: () => undefined }));
    }
  });

  protected readonly state = computed<{ kind: 'trial'; daysLeft: number } | { kind: 'readOnly'; until: string } | null>(() => {
    const s = this.meBilling.current();
    if (!s) return null;
    if (s.status === SubscriptionStatus.EXPIRED && s.readOnlyUntil) return { kind: 'readOnly', until: s.readOnlyUntil };
    // A plan chosen during the trial (autoRenew) starts on its own: nothing to ask.
    if (s.status === SubscriptionStatus.TRIAL && !s.autoRenew && s.trialEnd && this.canManage()) {
      return { kind: 'trial', daysLeft: Math.max(1, Math.ceil((Date.parse(s.trialEnd) - Date.now()) / DAY_MS)) };
    }
    return null;
  });
}
