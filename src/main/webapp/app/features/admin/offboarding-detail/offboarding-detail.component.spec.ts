import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { advanceTo, clear } from 'jest-date-mock';
import { of } from 'rxjs';

import { AccountService } from 'app/core/auth/account.service';

import { EmployeeProcessStatus } from '../models/enums/employee-process-status.enum';
import { OffboardingProcess, OffboardingTask } from '../models/offboarding.model';
import { OffboardingService } from '../services/offboarding.service';
import OffboardingDetailComponent from './offboarding-detail.component';

interface Detail {
  process: { set: (p: OffboardingProcess) => void };
  daysToLastDay: () => number;
  groups: () => { key: string; tasks: OffboardingTask[] }[];
  handover: () => { key: string; done: boolean }[];
  timeline: () => { total: number; elapsed: number } | null;
}

function task(title: string, dueDate: string, completed = false): OffboardingTask {
  return {
    taskId: title,
    title,
    description: null,
    dueDate,
    completed,
    completionDate: completed ? '2026-10-15' : null,
    assignedToId: null,
    assignedToName: null,
    completionNotes: null,
  };
}

const PROCESS: OffboardingProcess = {
  processId: 'o-1',
  workflowId: null,
  employeeId: 'leaver',
  employeeName: 'Tom Fischer',
  employeeEmail: null,
  workflowStatus: 'IN_PROGRESS',
  processStatus: EmployeeProcessStatus.IN_PROGRESS,
  currentState: null,
  startDate: '2026-10-08',
  lastWorkingDate: '2026-10-28',
  offboardingReason: 'Moving abroad',
  terminationReasonId: null,
  terminationReasonName: 'Resignation',
  exitInterview: true,
  completionPercentage: 33,
  tasks: [
    task('Return Company Assets', '2026-10-28'),
    task('Knowledge Transfer Documentation', '2026-10-12', true),
    task('Project Handover', '2026-10-16'),
  ],
  progress: {
    pendingRequestsProcessed: false,
    settlementCalculated: false,
    benefitsTerminated: false,
    knowledgeTransferCompleted: true,
    assetsReturned: false,
    exitInterviewConducted: false,
    accessRevoked: false,
    totalTasks: 3,
    completedTasks: 1,
  },
  settlement: {
    remainingLeaveDays: null,
    leaveEncashment: null,
    pendingExpenseReimbursements: 120,
    pendingOvertimePay: null,
    totalSettlement: 120,
  },
  createdDate: null,
  lastModifiedDate: null,
};

describe('OffboardingDetailComponent', () => {
  let detail: Detail;

  beforeEach(() => {
    advanceTo(new Date(2026, 9, 18, 9, 0)); // 18 Oct 2026
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OffboardingService, useValue: { find: () => of(PROCESS) } },
        { provide: AccountService, useValue: { hasPermission: () => true, hasAnyPermission: () => true } },
      ],
    });
    const fixture = TestBed.createComponent(OffboardingDetailComponent);
    fixture.componentRef.setInput('id', 'o-1');
    detail = fixture.componentInstance as unknown as Detail;
    detail.process.set(PROCESS);
  });

  afterEach(() => clear());

  it('counts down to the last working day across the window', () => {
    expect(detail.daysToLastDay()).toBe(10);
    expect(detail.timeline()).toEqual(expect.objectContaining({ total: 20, elapsed: 10 }));
  });

  it('groups the hand-over by state in due-date order', () => {
    expect(detail.groups().map(g => [g.key, g.tasks.map(t => t.title)])).toEqual([
      ['overdue', ['Project Handover']],
      ['todo', ['Return Company Assets']],
      ['done', ['Knowledge Transfer Documentation']],
    ]);
  });

  it('shows the hand-over checks from the checklist', () => {
    expect(
      detail
        .handover()
        .filter(h => h.done)
        .map(h => h.key),
    ).toEqual(['knowledgeTransferCompleted']);
  });
});

describe('OffboardingService', () => {
  let service: OffboardingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(OffboardingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists one employee across statuses and cancels with a reason', () => {
    service.list({ employeeId: 'e-1', status: [EmployeeProcessStatus.COMPLETED, EmployeeProcessStatus.CANCELLED] }).subscribe();
    const list = http.expectOne(r => r.url.endsWith('api/hr/workflows/offboarding'));
    expect(list.request.params.get('employeeId')).toBe('e-1');
    expect(list.request.params.getAll('status')).toEqual(['COMPLETED', 'CANCELLED']);
    list.flush({ content: [] });

    service.cancel('o-1', 'Decided to stay').subscribe();
    const cancel = http.expectOne(r => r.url.endsWith('api/hr/workflows/offboarding/o-1/cancel'));
    expect(cancel.request.params.get('reason')).toBe('Decided to stay');
    cancel.flush(null);
  });
});
