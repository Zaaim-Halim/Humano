package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.domain.hr.ReviewCycle;
import com.humano.dto.hr.workflow.responses.PerformanceAnalyticsResponse;
import com.humano.dto.hr.workflow.responses.PerformanceAnalyticsResponse.CycleStats;
import com.humano.dto.hr.workflow.responses.PerformanceAnalyticsResponse.DepartmentStats;
import com.humano.dto.hr.workflow.responses.PerformanceAnalyticsResponse.Stats;
import com.humano.repository.hr.ImprovementPlanRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.projection.ReviewStatRow;
import com.humano.repository.hr.workflow.ReviewCycleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performance analytics, computed on the server from the latest cycles of one type: completion, the
 * final rating distribution and its trend, calibration movement, and promotion and improvement-plan
 * rates — overall and per department. Counts only, never review text, and no demographic breakdowns.
 *
 * <p>Definitions (the UI shows them too): completion is over all reviews, acknowledgement over shared
 * ones; the distribution, average, movement, promotion and plan rates are over <b>rated</b> reviews —
 * written by the reviewer, with a final rating. Movement compares the final rating with the
 * reviewer's own, so it counts calibration and dispute resolutions alike.
 */
@Service
@Transactional(value = "tenantTransactionManager", readOnly = true)
public class PerformanceAnalyticsService {

    static final int MAX_CYCLES = 12;

    private final ReviewCycleRepository cycleRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final ImprovementPlanRepository planRepository;

    public PerformanceAnalyticsService(
        ReviewCycleRepository cycleRepository,
        PerformanceReviewRepository reviewRepository,
        ImprovementPlanRepository planRepository
    ) {
        this.cycleRepository = cycleRepository;
        this.reviewRepository = reviewRepository;
        this.planRepository = planRepository;
    }

    /** The latest {@code limit} cycles of {@code type} that have started, newest first. */
    public PerformanceAnalyticsResponse analytics(ReviewCycleType type, int limit) {
        List<ReviewCycle> cycles = cycleRepository
            .findByTypeOrderByReviewPeriodEndDescStartDateDesc(type)
            .stream()
            .filter(c -> c.getPhase() != ReviewCyclePhase.DRAFT)
            .limit(Math.max(1, Math.min(limit, MAX_CYCLES)))
            .toList();
        if (cycles.isEmpty()) {
            return new PerformanceAnalyticsResponse(type, List.of());
        }
        List<UUID> ids = cycles.stream().map(ReviewCycle::getId).toList();
        Map<UUID, List<ReviewStatRow>> rows = reviewRepository
            .findStatRowsByCycleIds(ids)
            .stream()
            .collect(Collectors.groupingBy(ReviewStatRow::cycleId));
        Set<UUID> withPlan = new HashSet<>(planRepository.findReviewIdsByCycleIds(ids));
        return new PerformanceAnalyticsResponse(
            type,
            cycles.stream().map(c -> cycleStats(c, rows.getOrDefault(c.getId(), List.of()), withPlan)).toList()
        );
    }

    static CycleStats cycleStats(ReviewCycle cycle, List<ReviewStatRow> rows, Set<UUID> withPlan) {
        Map<UUID, List<ReviewStatRow>> byDepartment = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        for (ReviewStatRow row : rows) {
            byDepartment.computeIfAbsent(row.departmentId(), k -> new ArrayList<>()).add(row);
            names.putIfAbsent(row.departmentId(), row.departmentName());
        }
        List<DepartmentStats> departments = byDepartment
            .entrySet()
            .stream()
            .map(e -> new DepartmentStats(e.getKey(), names.get(e.getKey()), stats(e.getValue(), withPlan)))
            // Named departments by name, people without one last.
            .sorted(
                Comparator.comparing((DepartmentStats d) -> d.name() == null).thenComparing(d ->
                    Objects.toString(d.name(), "").toLowerCase()
                )
            )
            .toList();
        return new CycleStats(
            cycle.getId(),
            cycle.getName(),
            cycle.getPhase(),
            cycle.getReviewPeriodStart(),
            cycle.getReviewPeriodEnd(),
            stats(rows, withPlan),
            departments
        );
    }

    /** Counts over some reviews; see the class comment for each base. */
    static Stats stats(Collection<ReviewStatRow> rows, Set<UUID> withPlan) {
        int self = 0, manager = 0, shared = 0, acknowledged = 0, disputed = 0;
        int rated = 0, sum = 0, raised = 0, lowered = 0, promotion = 0, plans = 0;
        int[] distribution = new int[5];
        for (ReviewStatRow r : rows) {
            if (r.selfSubmittedAt() != null) self++;
            if (r.managerSubmittedAt() != null) manager++;
            if (r.status() == PerformanceReviewStatus.SHARED) {
                shared++;
                if (r.acknowledgedAt() != null) acknowledged++;
                if (r.disputed()) disputed++;
            }
            Integer rating = r.rating();
            if (r.managerSubmittedAt() == null || rating == null || rating < 1 || rating > 5) {
                continue;
            }
            rated++;
            sum += rating;
            distribution[rating - 1]++;
            if (r.managerRating() != null) {
                if (rating > r.managerRating()) raised++;
                if (rating < r.managerRating()) lowered++;
            }
            if (r.recommendForPromotion()) promotion++;
            if (withPlan.contains(r.reviewId())) plans++;
        }
        return new Stats(
            rows.size(),
            self,
            manager,
            shared,
            acknowledged,
            disputed,
            rated,
            List.of(distribution[0], distribution[1], distribution[2], distribution[3], distribution[4]),
            rated == 0 ? null : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(rated), 2, RoundingMode.HALF_UP),
            raised,
            lowered,
            promotion,
            plans
        );
    }
}
