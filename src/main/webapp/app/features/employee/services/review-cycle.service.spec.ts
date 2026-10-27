import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ReviewCycleService } from './review-cycle.service';

describe('ReviewCycleService', () => {
  let service: ReviewCycleService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ReviewCycleService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('moves a cycle to a phase through its start endpoint', () => {
    service.startPhase('c-1', 'manager-review').subscribe();

    const req = http.expectOne(r => r.url.endsWith('api/hr/workflows/review-cycles/c-1/phases/manager-review/start'));
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it("filters a cycle's reviews by status only when one is given", () => {
    service.reviews('c-1', 'SHARED', { page: 0, size: 1000 }).subscribe();
    const shared = http.expectOne(r => r.url.endsWith('api/hr/workflows/review-cycles/c-1/reviews'));
    expect(shared.request.params.get('status')).toBe('SHARED');
    expect(shared.request.params.get('size')).toBe('1000');
    shared.flush({ content: [] });

    service.reviews('c-1').subscribe();
    const all = http.expectOne(r => r.url.endsWith('api/hr/workflows/review-cycles/c-1/reviews'));
    expect(all.request.params.has('status')).toBe(false);
    all.flush({ content: [] });
  });

  it('records a feedback meeting with notes, or with an empty body', () => {
    service.recordFeedbackMeeting('c-1', 'e-1', 'Agreed on goals').subscribe();
    const withNotes = http.expectOne(r => r.url.endsWith('api/hr/workflows/review-cycles/c-1/feedback/e-1'));
    expect(withNotes.request.body).toEqual({ notes: 'Agreed on goals' });
    withNotes.flush({});

    service.recordFeedbackMeeting('c-1', 'e-1').subscribe();
    const bare = http.expectOne(r => r.url.endsWith('api/hr/workflows/review-cycles/c-1/feedback/e-1'));
    expect(bare.request.body).toEqual({});
    bare.flush({});
  });

  it('takes a review out of its cycle with a reason, and puts it back', () => {
    service.exclude('c-1', 'r-1', 'On leave').subscribe();
    const exclude = http.expectOne(r => r.url.endsWith('api/hr/workflows/review-cycles/c-1/reviews/r-1/exclusion'));
    expect(exclude.request.method).toBe('POST');
    expect(exclude.request.body).toEqual({ reason: 'On leave' });
    exclude.flush({});

    service.restore('c-1', 'r-1').subscribe();
    const restore = http.expectOne(r => r.url.endsWith('api/hr/workflows/review-cycles/c-1/reviews/r-1/exclusion'));
    expect(restore.request.method).toBe('DELETE');
    restore.flush({});
  });

  it('searches people to add and adds them', () => {
    service.addableEmployees('c-1', 'car').subscribe();
    const search = http.expectOne(r => r.url.endsWith('api/hr/workflows/review-cycles/c-1/addable-employees'));
    expect(search.request.params.get('query')).toBe('car');
    search.flush([]);

    service.addEmployees('c-1', ['e-1', 'e-2']).subscribe();
    const add = http.expectOne(r => r.url.endsWith('api/hr/workflows/review-cycles/c-1/reviews'));
    expect(add.request.method).toBe('POST');
    expect(add.request.body).toEqual({ employeeIds: ['e-1', 'e-2'] });
    add.flush([]);
  });

  it('moves a cycle back a phase', () => {
    service.moveBack('c-1', { reason: 'Too early', selfAssessmentDeadline: '2026-11-20' }).subscribe();

    const req = http.expectOne(r => r.url.endsWith('api/hr/workflows/review-cycles/c-1/phases/back'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Too early', selfAssessmentDeadline: '2026-11-20' });
    req.flush({});
  });

  it('reads one review outside its cycle path', () => {
    service.review('r-1').subscribe();

    const req = http.expectOne(r => r.url.endsWith('api/hr/workflows/review-cycles/reviews/r-1'));
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
