import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { BadgeTone } from 'app/config/status-map';
import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import { LEGAL } from 'app/features/legal/legal-info';
import {
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  DrawerComponent,
  EmptyStateComponent,
  FormFieldComponent,
  InputComponent,
  PageHeaderComponent,
  SkeletonRowComponent,
  TextareaComponent,
  ToastService,
} from 'app/shared/ui';

import { DeletionState, NewSubprocessorNotice, ScheduledDeletions, SubprocessorNotice } from '../../models/compliance.model';
import { PlatformComplianceService } from '../../services/platform-compliance.service';

const DAY = 24 * 60 * 60 * 1000;

/** `yyyy-mm-dd`, `days` from today (UTC, as the server checks it). */
function isoDateIn(days: number): string {
  return new Date(Date.now() + days * DAY).toISOString().slice(0, 10);
}

/**
 * Owner console — data protection (`/platform/compliance`). **Scheduled deletions**: workspaces whose
 * subscription ended, with when their admin is warned and when they are deleted, as the nightly job will
 * do it — and a flag when a warning is overdue (the job never deletes without it). **Sub-processor
 * notices**: announce a new sub-processor to every customer, at least 30 days ahead, as the DPA requires;
 * the list is the record that each notice went out.
 */
@Component({
  selector: 'hum-console-compliance',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    TranslatePipe,
    LucideAngularModule,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    DrawerComponent,
    EmptyStateComponent,
    FormFieldComponent,
    InputComponent,
    PageHeaderComponent,
    SkeletonRowComponent,
    TextareaComponent,
  ],
  templateUrl: './compliance.component.html',
})
export default class ConsoleComplianceComponent {
  private readonly api = inject(PlatformComplianceService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);
  private readonly account = inject(AccountService).trackCurrentAccount();

  protected readonly noticeDays = LEGAL.subprocessorNoticeDays;
  protected readonly deletions = signal<ScheduledDeletions | null>(null);
  protected readonly notices = signal<SubprocessorNotice[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly canSend = computed(() => !!this.account()?.permissions?.includes(Permission.MANAGE_PLATFORM_BILLING));

  protected readonly counts = computed(() => {
    const items = this.deletions()?.items ?? [];
    const now = Date.now();
    return {
      total: items.length,
      warned: items.filter(i => i.state === 'WARNED').length,
      nextWeek: items.filter(i => Date.parse(i.deleteOn) - now < 7 * DAY).length,
      overdue: items.filter(i => i.warningOverdue).length,
    };
  });

  // ---- New notice
  protected readonly draft = signal<NewSubprocessorNotice | null>(null);
  protected readonly recipients = signal<number | null>(null);
  protected readonly minDate = isoDateIn(LEGAL.subprocessorNoticeDays);
  protected readonly draftValid = computed(() => {
    const d = this.draft();
    return (
      !!d &&
      !!d.name.trim() &&
      !!d.purpose.trim() &&
      !!d.dataCategories.trim() &&
      !!d.location.trim() &&
      !!d.effectiveFrom &&
      d.effectiveFrom >= this.minDate
    );
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.error.set(null);
    this.api.deletions().subscribe({
      next: d => this.deletions.set(d),
      error: err => this.error.set(normalizeHttpError(err)),
    });
    this.api.notices().subscribe({
      next: n => this.notices.set(n),
      error: err => this.error.set(normalizeHttpError(err)),
    });
  }

  protected stateTone(state: DeletionState, overdue: boolean): BadgeTone {
    if (overdue) return 'danger';
    return state === 'WARNED' ? 'warning' : state === 'WARNING_DUE' ? 'info' : 'neutral';
  }

  protected openNotice(): void {
    this.draft.set({ name: '', purpose: '', dataCategories: '', location: '', effectiveFrom: this.minDate });
    this.recipients.set(null);
    this.api.recipientCount().subscribe({ next: n => this.recipients.set(n), error: () => this.recipients.set(null) });
  }

  protected patch(p: Partial<NewSubprocessorNotice>): void {
    const d = this.draft();
    if (d) this.draft.set({ ...d, ...p });
  }

  protected send(): void {
    const d = this.draft();
    if (!d || !this.draftValid()) return;
    this.busy.set(true);
    this.api.sendNotice(d).subscribe({
      next: n => {
        this.busy.set(false);
        this.draft.set(null);
        this.notices.update(list => [n, ...(list ?? [])]);
        const problems = [
          n.missingCount ? this.t('humano.console.compliance.notice.missing', { n: n.missingCount }) : '',
          n.failedCount ? this.t('humano.console.compliance.notice.failed', { n: n.failedCount }) : '',
        ].filter(Boolean);
        this.toast.show({
          tone: n.failedCount ? 'danger' : n.missingCount ? 'warning' : 'success',
          title: this.t('humano.console.compliance.notice.sent', { n: n.recipientCount }),
          description: problems.length ? problems.join(' ') : undefined,
        });
      },
      error: err => {
        this.busy.set(false);
        this.toast.show({ tone: 'danger', title: normalizeHttpError(err) });
      },
    });
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return this.translate.instant(key, params) as string;
  }
}
