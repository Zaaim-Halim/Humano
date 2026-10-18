import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule } from 'lucide-angular';

import { Permission } from 'app/config/permission.constants';
import { AccountService } from 'app/core/auth/account.service';
import { BadgeComponent, ButtonComponent, ProgressComponent, SkeletonComponent } from 'app/shared/ui';

import { ACTIVE_PROCESS_STATUSES, EmployeeProcessStatus, EmployeeProcessSummary, OnboardingService } from '../index';

const ALL_STATUSES = Object.values(EmployeeProcessStatus);

/**
 * Onboarding card on an employee's profile — their latest onboarding (progress, overdue tasks,
 * when it finished) with a link to its checklist, and "Start onboarding" when none is running
 * (`MANAGE_ONBOARDING` + `READ_EMPLOYEE`). Renders nothing for viewers who can't see onboarding.
 */
@Component({
  selector: 'hum-employee-onboarding-card',
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
    @if (canView) {
      <section class="hum-onb-card">
        <div class="hum-onb-card__head">
          <h3 class="hum-onb-card__title"><lucide-icon name="user-plus" [size]="16" />{{ 'humano.onboarding.title' | translate }}</h3>
          @if (latest(); as p) {
            <hum-badge [status]="p.status" />
          }
        </div>

        @if (processes() === null) {
          <hum-skeleton [height]="44" />
        } @else if (latest(); as p) {
          @if (isActive(p)) {
            <hum-progress
              [value]="p.completionPercentage"
              [tone]="p.overdueTasks > 0 ? 'warning' : 'brand'"
              [label]="'humano.onboarding.tasksDone' | translate: { done: p.completedTasks, total: p.totalTasks }"
              showValue
            />
            <p class="hum-onb-card__meta">
              @if (p.overdueTasks > 0) {
                <strong class="hum-onb-overdue">{{ 'humano.onboarding.tasksOverdue' | translate: { count: p.overdueTasks } }}</strong>
              }
              {{ 'humano.onboarding.finishBy' | translate: { date: (p.dueDate | date: 'mediumDate') } }}
            </p>
          } @else {
            <p class="hum-onb-card__meta">
              {{
                (p.status === 'COMPLETED' ? 'humano.onboarding.completedOn' : 'humano.onboarding.cancelledOnDate')
                  | translate: { date: ((p.status === 'COMPLETED' ? p.completionDate : p.lastModifiedDate) | date: 'mediumDate') }
              }}
            </p>
          }
          <div class="hum-onb-card__actions">
            <hum-button variant="secondary" size="sm" icon="list-checks" [routerLink]="['/onboarding', p.processId]">{{
              'humano.onboarding.openChecklist' | translate
            }}</hum-button>
            @if (canStart && !isActive(p)) {
              <hum-button variant="ghost" size="sm" icon="user-plus" routerLink="/onboarding" [queryParams]="{ start: employeeId() }">{{
                'humano.onboarding.startAgain' | translate
              }}</hum-button>
            }
          </div>
        } @else {
          <p class="hum-onb-card__meta">{{ 'humano.onboarding.notStarted' | translate }}</p>
          @if (canStart) {
            <div class="hum-onb-card__actions">
              <hum-button variant="secondary" size="sm" icon="user-plus" routerLink="/onboarding" [queryParams]="{ start: employeeId() }">{{
                'humano.onboarding.start' | translate
              }}</hum-button>
            </div>
          }
        }
      </section>
    }
  `,
})
export class EmployeeOnboardingCardComponent {
  readonly employeeId = input.required<string>();

  private readonly onboardingService = inject(OnboardingService);
  private readonly account = inject(AccountService);

  protected readonly canView = this.account.hasAnyPermission([Permission.VIEW_ONBOARDING_PROGRESS, Permission.MANAGE_ONBOARDING]);
  protected readonly canStart =
    this.account.hasPermission(Permission.MANAGE_ONBOARDING) && this.account.hasPermission(Permission.READ_EMPLOYEE);

  protected readonly processes = signal<EmployeeProcessSummary[] | null>(null);
  /** Newest first: an active one if any, else the most recently started. */
  protected readonly latest = computed(() => {
    const list = this.processes() ?? [];
    return list.find(p => this.isActive(p)) ?? (list.length ? list[0] : null);
  });

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
    this.onboardingService.list({ employeeId, status: ALL_STATUSES }, { page: 0, size: 10, sort: ['startDate,desc'] }).subscribe({
      next: page => this.processes.set(page.content),
      error: () => this.processes.set([]),
    });
  }
}
