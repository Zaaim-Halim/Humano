import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

import { normalizeHttpError } from 'app/core/api';
import { AccountService } from 'app/core/auth/account.service';
import { AlertComponent, ButtonComponent, EmptyStateComponent, PageHeaderComponent, SkeletonComponent, ToastService } from 'app/shared/ui';
import { stripHtml } from 'app/shared/util/strip-html';

import { AccountManagementService, Session } from '../account-management.service';

/** Active sessions — list (`GET /api/account/sessions`) + revoke. In-shell. */
@Component({
  selector: 'hum-sessions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe, PageHeaderComponent, ButtonComponent, AlertComponent, EmptyStateComponent, SkeletonComponent],
  templateUrl: './sessions.component.html',
})
export default class SessionsComponent {
  private readonly api = inject(AccountManagementService);
  private readonly accountService = inject(AccountService);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  private readonly account = this.accountService.trackCurrentAccount();
  protected readonly sessions = signal<Session[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly title = signal(this.translate.instant('sessions.title', { username: '' }));

  constructor() {
    effect(() => {
      const a = this.account();
      if (a) {
        this.title.set(this.translate.instant('sessions.title', { username: a.login }));
      }
    });
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.sessions().subscribe({
      next: list => {
        this.sessions.set(list);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(normalizeHttpError(err));
        this.loading.set(false);
      },
    });
  }

  protected invalidate(session: Session): void {
    this.api.invalidate(session.series).subscribe({
      next: () => {
        this.toast.success(stripHtml(this.translate.instant('sessions.messages.success')));
        this.load();
      },
      error: () => this.toast.danger(stripHtml(this.translate.instant('sessions.messages.error'))),
    });
  }
}
