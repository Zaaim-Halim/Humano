import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from 'app/core/api';
import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { createRequestOption } from 'app/core/request/request-util';

import { ApprovalDecisionRequest, ApprovalWorkflow, PendingApprovalSummary } from '../models/approval.model';

/**
 * Approval workflows — `/api/hr/workflows/approvals`. Backs the Manager
 * Approvals inbox and the HR-admin approvals surface.
 *
 * Listing is keyed by approver/requestor **employee** id (`ApprovalRequest`
 * joins both to `Employee`), and there is no global/admin pending queue — so a
 * caller must know the approver's employee id to discover anything. `decide`
 * and `withdraw` act on a known `approvalId` and derive the actor from the
 * session, so they need no employee id.
 *
 * Note the shape asymmetry: {@link pendingForApprover} is unpaged (`List`),
 * {@link pendingForApproverPaged} returns a `Page`.
 */
@Injectable({ providedIn: 'root' })
export class ApprovalService {
  private readonly http = inject(HttpClient);
  private readonly resourceUrl = inject(ApplicationConfigService).getEndpointFor('api/hr/workflows/approvals');

  /** `GET /pending/{approverId}` — unpaged. */
  pendingForApprover(approverId: string): Observable<PendingApprovalSummary[]> {
    return this.http.get<PendingApprovalSummary[]>(`${this.resourceUrl}/pending/${encodeURIComponent(approverId)}`);
  }

  /** `GET /pending/{approverId}/paged`. */
  pendingForApproverPaged(approverId: string, req?: PageRequest): Observable<Page<PendingApprovalSummary>> {
    return this.http.get<Page<PendingApprovalSummary>>(`${this.resourceUrl}/pending/${encodeURIComponent(approverId)}/paged`, {
      params: createRequestOption(req),
    });
  }

  /** `GET /pending/{approverId}/count`. */
  countPending(approverId: string): Observable<number> {
    return this.http.get<number>(`${this.resourceUrl}/pending/${encodeURIComponent(approverId)}/count`);
  }

  /** `GET /{approvalId}` — full detail incl. the multi-level chain history. */
  find(approvalId: string): Observable<ApprovalWorkflow> {
    return this.http.get<ApprovalWorkflow>(`${this.resourceUrl}/${encodeURIComponent(approvalId)}`);
  }

  /** `GET /requestor/{requestorId}` — paged approvals a person submitted. */
  byRequestor(requestorId: string, req?: PageRequest): Observable<Page<ApprovalWorkflow>> {
    return this.http.get<Page<ApprovalWorkflow>>(`${this.resourceUrl}/requestor/${encodeURIComponent(requestorId)}`, {
      params: createRequestOption(req),
    });
  }

  /** `POST /{approvalId}/decide` — actor derived from the session (no approverId in body). */
  decide(approvalId: string, body: ApprovalDecisionRequest): Observable<ApprovalWorkflow> {
    return this.http.post<ApprovalWorkflow>(`${this.resourceUrl}/${encodeURIComponent(approvalId)}/decide`, body);
  }

  /** `POST /{approvalId}/withdraw?reason=`. */
  withdraw(approvalId: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.resourceUrl}/${encodeURIComponent(approvalId)}/withdraw`, null, {
      params: createRequestOption({ reason }),
    });
  }
}
