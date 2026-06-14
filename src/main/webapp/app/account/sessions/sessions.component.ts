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
  template: `
    <div class="hum-page" style="max-width:720px">
      <hum-page-header [title]="title()" />

      @if (loading()) {
        <div style="display:grid;gap:var(--space-2)">
          <hum-skeleton [height]="44" />
          <hum-skeleton [height]="44" />
          <hum-skeleton [height]="44" />
        </div>
      } @else if (error(); as e) {
        <hum-alert tone="danger" [title]="e">
          <hum-button variant="outline" size="sm" (click)="load()">{{ 'humano.action.retry' | translate }}</hum-button>
        </hum-alert>
      } @else if (sessions().length === 0) {
        <hum-empty-state icon="shield-x" [title]="'sessions.title' | translate" />
      } @else {
        <table class="hum-table">
          <thead>
            <tr>
              <th>{{ 'sessions.table.ipaddress' | translate }}</th>
              <th>{{ 'sessions.table.useragent' | translate }}</th>
              <th>{{ 'sessions.table.date' | translate }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (s of sessions(); track s.series) {
              <tr>
                <td>{{ s.ipAddress }}</td>
                <td>{{ s.userAgent }}</td>
                <td class="tabular-nums">{{ s.tokenDate }}</td>
                <td style="text-align:right">
                  <hum-button variant="outline" size="sm" (click)="invalidate(s)">{{ 'sessions.table.button' | translate }}</hum-button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
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
