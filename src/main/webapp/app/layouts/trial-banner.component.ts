import { ChangeDetectionStrategy, Component, computed, effect, inject, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { filter, map } from 'rxjs';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { planIncludes } from 'app/features/platform/models/entitlements.model';
import { SubscriptionStatus } from 'app/features/platform/models/enums/subscription-status.enum';
import { MeBillingService } from 'app/features/platform/services/me-billing.service';
import { AlertComponent } from 'app/shared/ui';

import { SHELL_NAV } from './shell-nav';

const DAY_MS = 86_400_000;

type BannerState =
  | { kind: 'readOnly'; until: string }
  | { kind: 'module'; name: string; plan: string | null }
  | { kind: 'trial'; daysLeft: number };

/**
 * The plan line above every page of a customer workspace, most important first:
 * - a trial that ended without a plan: the workspace is read-only (until when), for everyone;
 * - a page whose module is outside the plan: it is read-only here, and why;
 * - during the trial, for billing admins: the days left.
 * Billing admins also get the link to choose a plan. Nothing shows for paying customers on a plan
 * that covers the page, or for demo workspaces (no subscription).
 */
@Component({
  selector: 'hum-trial-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, TranslatePipe, AlertComponent],
  template: `
    @if (state(); as s) {
      <hum-alert
        [tone]="s.kind === 'trial' ? (s.daysLeft <= 3 ? 'warning' : 'info') : s.kind === 'module' ? 'info' : 'warning'"
        style="margin-bottom: var(--space-4)"
      >
        @switch (s.kind) {
          @case ('trial') {
            {{ (s.daysLeft === 1 ? 'humano.trial.lastDay' : 'humano.trial.daysLeft') | translate: { n: s.daysLeft } }}
          }
          @case ('module') {
            {{ (s.plan ? 'humano.plan.moduleReadOnly' : 'humano.plan.moduleReadOnlyNoPlan') | translate: { name: s.name, plan: s.plan } }}
          }
          @case ('readOnly') {
            {{ 'humano.trial.readOnly' | translate: { date: (s.until | date: 'longDate') } }}
          }
        }
        @if (canManage()) {
          <a routerLink="/settings/billing" style="margin-left: var(--space-2); font-weight: 600">{{
            'humano.trial.choosePlan' | translate
          }}</a>
        } @else if (s.kind !== 'trial') {
          {{ 'humano.trial.askAdmin' | translate }}
        }
      </hum-alert>
    }
  `,
})
export class TrialBannerComponent {
  private readonly meBilling = inject(MeBillingService);
  private readonly router = inject(Router);
  private readonly account = inject(AccountService).trackCurrentAccount();

  protected readonly canManage = computed(() => !!this.account()?.permissions?.includes(Permission.MANAGE_SUBSCRIPTIONS));

  private readonly url = toSignal(
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /** The plan feature of the screen being shown, from the nav item whose link it is under. */
  private readonly pageFeature = computed(() => {
    const url = this.url().split('?')[0];
    for (const group of SHELL_NAV) {
      for (const item of group.items) {
        if (item.feature && (url === item.link || url.startsWith(item.link + '/'))) return item.feature;
      }
    }
    return null;
  });

  private readonly load = effect(() => {
    if (this.account() && this.meBilling.current() === undefined) {
      untracked(() => this.meBilling.currentSubscription().subscribe({ error: () => undefined }));
    }
  });

  protected readonly state = computed<BannerState | null>(() => {
    const s = this.meBilling.current();
    if (s?.status === SubscriptionStatus.EXPIRED && s.readOnlyUntil) return { kind: 'readOnly', until: s.readOnlyUntil };

    const feature = this.pageFeature();
    const entitlements = this.meBilling.entitlements();
    if (feature && !planIncludes(entitlements, feature)) {
      const name = entitlements?.features.find(f => f.code === feature)?.name ?? feature;
      return { kind: 'module', name, plan: entitlements?.planName ?? null };
    }

    // A plan chosen during the trial (autoRenew) starts on its own: nothing to ask.
    if (s?.status === SubscriptionStatus.TRIAL && !s.autoRenew && s.trialEnd && this.canManage()) {
      return { kind: 'trial', daysLeft: Math.max(1, Math.ceil((Date.parse(s.trialEnd) - Date.now()) / DAY_MS)) };
    }
    return null;
  });
}
