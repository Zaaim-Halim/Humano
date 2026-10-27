package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The retention settings (the defaults until saved) and what they mean today: cycles whose review
 * period ended before {@code cutoff} are either {@code due} — archived and settled, deleted by the
 * next run — or {@code held}, with what holds them back.
 */
public record ReviewRetentionResponse(
    boolean enabled,
    int retainYears,
    boolean customized,
    LocalDate cutoff,
    List<Cycle> due,
    List<Cycle> held
) {
    public record Cycle(
        UUID id,
        String name,
        ReviewCycleType type,
        ReviewCyclePhase phase,
        LocalDate periodStart,
        LocalDate periodEnd,
        long reviews,
        Holds holds
    ) {}

    /**
     * What keeps a cycle past the cutoff: not archived yet, disputes not resolved, improvement plans
     * not closed, compensation proposals not applied.
     */
    public record Holds(boolean notArchived, long openDisputes, long openPlans, long pendingProposals) {
        public boolean any() {
            return notArchived || openDisputes > 0 || openPlans > 0 || pendingProposals > 0;
        }
    }
}
