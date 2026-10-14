package com.humano.security;

import com.humano.dto.hr.workflow.responses.ApprovalWorkflowResponse;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Who may act on an approval request. Approvals are routed to people (a requester's manager,
 * department head, …), not to roles, so the checks are relational:
 * <ul>
 *   <li>deciding — the request's current approver;</li>
 *   <li>withdrawing — the requester;</li>
 *   <li>viewing — the requester or anyone who is or was an approver on it;</li>
 *   <li>reading a person's queue or request history — that person.</li>
 * </ul>
 * Admins and HR managers ({@link #OVERSIGHT}) may do all of these for anyone.
 */
@Component
public class ApprovalAccess {

    private static final String[] OVERSIGHT = { AuthoritiesConstants.ADMIN, AuthoritiesConstants.HR_MANAGER };

    /** The caller may only act as {@code employeeId} (their own queue, their own requests). */
    public void requireSelf(UUID employeeId) {
        if (!isOversight() && !Objects.equals(currentUserId(), employeeId)) {
            throw new AccessDeniedException("You can only access your own approvals");
        }
    }

    /** The caller must be the request's current approver. */
    public void requireCurrentApprover(ApprovalWorkflowResponse approval) {
        if (!isOversight() && !Objects.equals(currentUserId(), approval.currentApproverId())) {
            throw new AccessDeniedException("This request is not waiting on you");
        }
    }

    /** The caller must be the request's requester. */
    public void requireRequestor(ApprovalWorkflowResponse approval) {
        if (!isOversight() && !Objects.equals(currentUserId(), approval.requestorId())) {
            throw new AccessDeniedException("Only the requester can withdraw this request");
        }
    }

    /** The caller must be the requester or an approver (current or past) on the request. */
    public void requireParticipant(ApprovalWorkflowResponse approval) {
        if (isOversight()) {
            return;
        }
        UUID me = currentUserId();
        boolean participant =
            Objects.equals(me, approval.requestorId()) ||
            Objects.equals(me, approval.currentApproverId()) ||
            (approval.approvalHistory() != null && approval.approvalHistory().stream().anyMatch(h -> Objects.equals(me, h.approverId())));
        if (!participant) {
            throw new AccessDeniedException("You are not part of this approval");
        }
    }

    private boolean isOversight() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(OVERSIGHT);
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId().orElse(null);
    }
}
