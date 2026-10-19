import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { advanceTo, clear } from 'jest-date-mock';
import { of } from 'rxjs';

import { AccountService } from 'app/core/auth/account.service';

import { EmployeeProcessStatus } from '../models/enums/employee-process-status.enum';
import { OnboardingProcess, OnboardingTask } from '../models/onboarding.model';
import { OnboardingService } from '../services/onboarding.service';
import OnboardingDetailComponent from './onboarding-detail.component';

/** Component internals under test (protected in the class). */
interface Detail {
  process: { set: (p: OnboardingProcess) => void };
  groups: () => { key: string; tasks: OnboardingTask[] }[];
  overdue: () => OnboardingTask[];
  timeline: () => { total: number; elapsed: number; daysLeft: number } | null;
  dueLabel: (t: OnboardingTask) => string;
  ownerFilter: { set: (v: string) => void };
}

function task(title: string, dueDate: string, completed = false, assignedToId: string | null = 'hire'): OnboardingTask {
  return {
    taskId: title,
    title,
    description: null,
    dueDate,
    completed,
    completionDate: completed ? '2026-10-12' : null,
    assignedToId,
    assignedToName: assignedToId ? 'Patrick Murphy' : null,
    completionNotes: null,
    link: null,
    linkSatisfied: null,
  };
}

const PROCESS: OnboardingProcess = {
  processId: 'p-1',
  workflowId: 'w-1',
  employeeId: 'hire',
  employeeName: 'Patrick Murphy',
  employeeEmail: null,
  workflowStatus: 'IN_PROGRESS',
  processStatus: EmployeeProcessStatus.IN_PROGRESS,
  currentState: null,
  startDate: '2026-10-08',
  dueDate: '2026-11-07',
  completionPercentage: 25,
  // Deliberately unordered, like the API.
  tasks: [
    task('Benefits Enrollment', '2026-10-22'),
    task('IT Systems Setup', '2026-10-10', true, null),
    task('Policy Acknowledgment', '2026-10-15'),
    task('Onboarding Review Meeting', '2026-10-19'),
  ],
  progress: {
    profileCreated: true,
    departmentAssigned: true,
    benefitsSetup: false,
    trainingsAssigned: false,
    documentsUploaded: false,
    systemAccessGranted: true,
    totalTasks: 4,
    completedTasks: 1,
  },
  createdDate: null,
  lastModifiedDate: null,
};

describe('OnboardingDetailComponent', () => {
  let detail: Detail;

  beforeEach(() => {
    advanceTo(new Date(2026, 9, 18, 9, 0)); // 18 Oct 2026, local time
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OnboardingService, useValue: { find: () => of(PROCESS) } },
        { provide: AccountService, useValue: { hasPermission: () => true, hasAnyPermission: () => true } },
      ],
    });
    const fixture = TestBed.createComponent(OnboardingDetailComponent);
    fixture.componentRef.setInput('id', 'p-1');
    detail = fixture.componentInstance as unknown as Detail;
    detail.process.set(PROCESS);
  });

  afterEach(() => clear());

  it('groups tasks into overdue, to do and done, each in due-date order', () => {
    expect(detail.groups().map(g => [g.key, g.tasks.map(t => t.title)])).toEqual([
      ['overdue', ['Policy Acknowledgment']],
      ['todo', ['Onboarding Review Meeting', 'Benefits Enrollment']],
      ['done', ['IT Systems Setup']],
    ]);
    expect(detail.overdue()).toHaveLength(1);
  });

  it('narrows the checklist to one owner, HR & IT included', () => {
    detail.ownerFilter.set('unassigned');
    expect(detail.groups().flatMap(g => g.tasks.map(t => t.title))).toEqual(['IT Systems Setup']);
  });

  it('places today on the onboarding window', () => {
    expect(detail.timeline()).toEqual(expect.objectContaining({ total: 30, elapsed: 10, daysLeft: 20 }));
  });

  it('labels due dates relative to today', () => {
    const [benefits, , policy, review] = PROCESS.tasks;
    expect(detail.dueLabel(policy)).toBe('humano.onboarding.overdueBy');
    expect(detail.dueLabel(review)).toBe('humano.onboarding.dueTomorrow');
    expect(detail.dueLabel(benefits)).toBe('humano.onboarding.dueIn');
  });
});
