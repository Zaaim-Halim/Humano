import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { BadgeComponent, ButtonComponent, ProgressComponent, SkeletonComponent } from 'app/shared/ui';

import { ACTIVE_PROCESS_STATUSES, EmployeeProcessStatus, EmployeeProcessSummary, OffboardingService, localIsoDate } from '../index';

const ALL_STATUSES = Object.values(EmployeeProcessStatus);

/**
 * Offboarding card on an employee's profile — their latest offboarding (last working day,
 * checklist progress, or how it ended) with a link to it, and "Start offboarding" for someone
 * who hasn't left and has none running (`MANAGE_OFFBOARDING` + `READ_EMPLOYEE`). Renders
 * nothing when there's nothing to show or the viewer can't see offboarding.
 */
@Component({
  selector: 'hum-employee-offboarding-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    DatePipe,
    TranslatePipe,
    LucideAngularModule,
    BadgeComponent,
    ButtonComponent,
    ProgressComponent,
    SkeletonComponent,
  ],
  template: `
    @if (canView && (latest() || (canStart && !terminated()))) {
      <section class="hum-onb-card">
        <div class="hum-onb-card__head">
          <h3 class="hum-onb-card__title"><lucide-icon name="user-minus" [size]="16" />{{ 'humano.offboarding.title' | translate }}</h3>
          @if (latest(); as p) {
            <hum-badge [status]="p.status" />
          }
        </div>
        @if (latest(); as p) {
          @if (isActive(p)) {
            <hum-progress
              [value]="p.completionPercentage"
              [tone]="p.overdueTasks > 0 ? 'warning' : 'brand'"
              [label]="'humano.onboarding.tasksDone' | translate: { done: p.completedTasks, total: p.totalTasks }"
              showValue
            />
          }
          <p class="hum-onb-card__meta">
            @if (p.status === 'CANCELLED') {
              {{ 'humano.onboarding.cancelledOnDate' | translate: { date: (p.lastModifiedDate | date: 'mediumDate') } }}
            } @else if (p.dueDate && p.dueDate < today) {
              {{ 'humano.offboarding.leftOnShort' | translate: { date: (p.dueDate | date: 'mediumDate') } }}
            } @else {
              {{ 'humano.offboarding.lastDayOn' | translate: { date: (p.dueDate | date: 'mediumDate') } }}
            }
          </p>
          <div class="hum-onb-card__actions">
            <hum-button variant="secondary" size="sm" icon="list-checks" [routerLink]="['/offboarding', p.processId]">{{
              'humano.onboarding.openChecklist' | translate
            }}</hum-button>
          </div>
        } @else {
          <p class="hum-onb-card__meta">{{ 'humano.offboarding.notStarted' | translate }}</p>
          <div class="hum-onb-card__actions">
            <hum-button variant="ghost" size="sm" icon="user-minus" routerLink="/offboarding" [queryParams]="{ start: employeeId() }">{{
              'humano.offboarding.start' | translate
            }}</hum-button>
          </div>
        }
      </section>
    } @else if (canView && processes() === null) {
      <hum-skeleton [height]="44" />
    }
  `,
})
export class EmployeeOffboardingCardComponent {
  readonly employeeId = input.required<string>();
  /** The employee's current status (`EmployeeStatus`); a terminated employee isn't offered a new offboarding. */
  readonly status = input<string | null>(null);

  private readonly offboardingService = inject(OffboardingService);
  private readonly account = inject(AccountService);

  protected readonly canView = this.account.hasAnyPermission([Permission.VIEW_OFFBOARDING, Permission.MANAGE_OFFBOARDING]);
  protected readonly canStart =
    this.account.hasPermission(Permission.MANAGE_OFFBOARDING) && this.account.hasPermission(Permission.READ_EMPLOYEE);
  protected readonly today = localIsoDate();

  protected readonly processes = signal<EmployeeProcessSummary[] | null>(null);
  protected readonly latest = computed(() => {
    const list = this.processes() ?? [];
    return list.find(p => this.isActive(p)) ?? (list.length ? list[0] : null);
  });
  protected readonly terminated = computed(() => this.status() === 'TERMINATED');

  constructor() {
    effect(() => {
      const id = this.employeeId();
      if (id && this.canView) untracked(() => this.load(id));
    });
  }

  protected isActive(p: EmployeeProcessSummary): boolean {
    return ACTIVE_PROCESS_STATUSES.includes(p.status);
  }

  private load(employeeId: string): void {
    this.processes.set(null);
    this.offboardingService.list({ employeeId, status: ALL_STATUSES }, { page: 0, size: 10, sort: ['startDate,desc'] }).subscribe({
      next: page => this.processes.set(page.content),
      error: () => this.processes.set([]),
    });
  }
}
