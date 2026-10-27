package com.humano.service.hr.workflow;

import com.humano.domain.hr.ReviewCycle;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes one review cycle and everything recorded under it, children before parents:
 *
 * <ol>
 *   <li>per review: question answers, competency and goal ratings, feedback requests, rating changes;</li>
 *   <li>improvement plans started from its reviews, with their objectives and check-ins;</li>
 *   <li>compensation proposals;</li>
 *   <li>approval requests about the cycle or its reviews (sign-offs), the workflows behind them with
 *       their deadlines and transitions;</li>
 *   <li>the reviews, the cycle's copies of its template (competencies, questions), the cycle itself
 *       (with its department and employee lists), and last the cycle's own workflow.</li>
 * </ol>
 *
 * <p>Goals and their check-ins, position history, bonuses, pay records and the audit log are not
 * part of a review and stay. Only {@link ReviewRetentionService} calls this, in a transaction of its
 * own per cycle, after checking the cycle is due.
 */
@Component
class ReviewCyclePurger {

    /** The ids of the cycle's reviews. */
    private static final String REVIEWS = "(select r.id from PerformanceReview r where r.cycle.id = :cycle)";

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    /** Deletes the (locked) cycle and returns how many rows of each kind went, by kind. */
    @Transactional(value = "tenantTransactionManager", propagation = Propagation.MANDATORY)
    public Map<String, Integer> purge(ReviewCycle cycle) {
        UUID cycleId = cycle.getId();
        UUID cycleWorkflowId = cycle.getWorkflowInstance() != null ? cycle.getWorkflowInstance().getId() : null;
        // Workflows started for the cycle or one of its reviews (approval sign-offs use the review's id).
        String workflows =
            "(select w.id from WorkflowInstance w where w.entityId = :cycle or w.entityId in " +
            REVIEWS +
            (cycleWorkflowId != null ? " or w.id = :workflow" : "") +
            ")";
        Map<String, Integer> counts = new LinkedHashMap<>();

        counts.put("answers", delete("delete from ReviewQuestionAnswer x where x.review.id in " + REVIEWS, cycleId, null));
        counts.put("competencyRatings", delete("delete from ReviewCompetencyRating x where x.review.id in " + REVIEWS, cycleId, null));
        counts.put("goalRatings", delete("delete from ReviewGoalRating x where x.review.id in " + REVIEWS, cycleId, null));
        counts.put("feedbackRequests", delete("delete from ReviewFeedbackRequest x where x.review.id in " + REVIEWS, cycleId, null));
        counts.put("ratingChanges", delete("delete from ReviewRatingChange x where x.review.id in " + REVIEWS, cycleId, null));

        String plans = "(select p.id from ImprovementPlan p where p.review.id in " + REVIEWS + ")";
        delete("delete from ImprovementPlanCheckIn x where x.plan.id in " + plans, cycleId, null);
        delete("delete from ImprovementPlanObjective x where x.plan.id in " + plans, cycleId, null);
        counts.put("improvementPlans", delete("delete from ImprovementPlan x where x.review.id in " + REVIEWS, cycleId, null));
        counts.put("compensationProposals", delete("delete from CompensationProposal x where x.cycle.id = :cycle", cycleId, null));

        counts.put(
            "approvalRequests",
            delete(
                "delete from ApprovalRequest x where x.entityId = :cycle or x.entityId in " +
                REVIEWS +
                " or x.workflowInstance.id in " +
                workflows,
                cycleId,
                cycleWorkflowId
            )
        );
        delete("delete from WorkflowDeadline x where x.workflowInstance.id in " + workflows, cycleId, cycleWorkflowId);
        delete("delete from WorkflowStateTransition x where x.workflowInstance.id in " + workflows, cycleId, cycleWorkflowId);
        // The review workflows now; the cycle's own one once the cycle no longer points at it.
        int workflowCount = delete("delete from WorkflowInstance x where x.entityId in " + REVIEWS, cycleId, null);

        counts.put("reviews", delete("delete from PerformanceReview x where x.cycle.id = :cycle", cycleId, null));
        delete("delete from ReviewCycleQuestion x where x.cycle.id = :cycle", cycleId, null);
        delete("delete from ReviewCycleCompetency x where x.cycle.id = :cycle", cycleId, null);
        // Removed as an entity, so its department and employee lists go with it.
        em.remove(cycle);
        em.flush();

        workflowCount += delete(
            "delete from WorkflowInstance x where x.entityId = :cycle" + (cycleWorkflowId != null ? " or x.id = :workflow" : ""),
            cycleId,
            cycleWorkflowId
        );
        counts.put("workflows", workflowCount);
        return counts;
    }

    private int delete(String jpql, UUID cycleId, UUID workflowId) {
        var query = em.createQuery(jpql).setParameter("cycle", cycleId);
        if (workflowId != null && jpql.contains(":workflow")) {
            query.setParameter("workflow", workflowId);
        }
        return query.executeUpdate();
    }
}
