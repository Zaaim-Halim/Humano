import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { EmployeeProcessStatus } from '../models/enums/employee-process-status.enum';
import { OnboardingService } from './onboarding.service';

describe('OnboardingService', () => {
  let service: OnboardingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(OnboardingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists history with one status param per status, plus paging and sort', () => {
    service
      .list(
        { status: [EmployeeProcessStatus.COMPLETED, EmployeeProcessStatus.CANCELLED] },
        { page: 0, size: 100, sort: ['lastModifiedDate,desc'] },
      )
      .subscribe();

    const req = http.expectOne(r => r.url.endsWith('api/hr/workflows/onboarding'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.getAll('status')).toEqual(['COMPLETED', 'CANCELLED']);
    expect(req.request.params.get('sort')).toBe('lastModifiedDate,desc');
    expect(req.request.params.has('employeeId')).toBe(false);
    req.flush({ content: [] });
  });

  it("filters one employee's onboardings", () => {
    service.list({ employeeId: 'e-1' }).subscribe();

    const req = http.expectOne(r => r.url.endsWith('api/hr/workflows/onboarding'));
    expect(req.request.params.get('employeeId')).toBe('e-1');
    expect(req.request.params.has('status')).toBe(false);
    req.flush({ content: [] });
  });

  it('cancels with the reason as a query parameter', () => {
    service.cancel('p-1', 'Offer withdrawn').subscribe();

    const req = http.expectOne(r => r.url.endsWith('api/hr/workflows/onboarding/p-1/cancel'));
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('reason')).toBe('Offer withdrawn');
    req.flush(null);
  });

  it('completes a task and replaces the checklist', () => {
    service.completeTask('p-1', { taskId: 't-1', successful: true, completionNotes: 'Done' }).subscribe();
    const complete = http.expectOne(r => r.url.endsWith('api/hr/workflows/onboarding/p-1/tasks/complete'));
    expect(complete.request.body).toEqual({ taskId: 't-1', successful: true, completionNotes: 'Done' });
    complete.flush({});

    service
      .saveChecklist({ defaultDays: 14, tasks: [{ title: 'Laptop', description: null, dueDay: 0, owner: 'HR', link: 'ACCOUNT' }] })
      .subscribe();
    const save = http.expectOne(r => r.url.endsWith('api/hr/workflows/onboarding-checklist'));
    expect(save.request.method).toBe('PUT');
    expect(save.request.body.defaultDays).toBe(14);
    save.flush({ defaultDays: 14, customized: true, tasks: [] });
  });
});
