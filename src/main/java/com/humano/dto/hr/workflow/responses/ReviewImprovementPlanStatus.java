package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.ImprovementPlanStatus;
import java.util.UUID;

/**
 * An improvement plan as seen from the review it may start from: whether the signed-in user can start
 * one now, and the latest plan started from this review, if any (that they may see).
 */
public record ReviewImprovementPlanStatus(boolean canStart, UUID planId, ImprovementPlanStatus status) {}
