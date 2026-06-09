package com.humano.repository.hr.projection;

import java.util.UUID;

/**
 * One aggregate row: number of employees directly assigned to a given unit.
 * <p>
 * Used to enrich an organizational-unit tree with per-node headcounts in a single
 * GROUP BY query, instead of per-node counts that would N+1.
 */
public record UnitHeadcountRow(UUID unitId, long headcount) {}
