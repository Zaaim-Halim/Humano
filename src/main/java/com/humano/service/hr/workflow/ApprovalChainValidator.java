package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.ApprovalType;
import com.humano.domain.hr.ApprovalChainConfig;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates approval-chain configurations.
 *
 * <p>Rules:
 * <ol>
 *   <li>A chain for a given {@link ApprovalType} must have at least one active step.</li>
 *   <li>Active steps' {@code sequence_order} values must form a gap-free 1..N sequence;
 *       holes (e.g. 1, 2, 4) are rejected because the orchestrator advances by index
 *       lookup and a hole would silently stall the workflow.</li>
 * </ol>
 *
 * Inactive steps are ignored — toggling {@code active=false} on a step is the deliberate
 * way to remove an approval level without losing its audit history.
 */
public final class ApprovalChainValidator {

    private ApprovalChainValidator() {}

    /**
     * Validate the chain. Throws {@link IllegalArgumentException} on the first failure with
     * a message identifying the offending {@link ApprovalType} so callers can surface a
     * tenant-friendly error.
     */
    public static void validate(ApprovalType type, Collection<ApprovalChainConfig> steps) {
        List<ApprovalChainConfig> active = steps
            .stream()
            .filter(s -> Boolean.TRUE.equals(s.getActive()))
            .sorted((a, b) -> Integer.compare(a.getSequenceOrder(), b.getSequenceOrder()))
            .collect(Collectors.toList());

        if (active.isEmpty()) {
            throw new IllegalArgumentException("Approval chain for " + type + " must have at least one active step");
        }

        for (int i = 0; i < active.size(); i++) {
            int expected = i + 1;
            Integer actual = active.get(i).getSequenceOrder();
            if (actual == null || actual != expected) {
                throw new IllegalArgumentException(
                    "Approval chain for " + type + " has a sequence gap: expected " + expected + " at position " + i + ", got " + actual
                );
            }
        }
    }
}
