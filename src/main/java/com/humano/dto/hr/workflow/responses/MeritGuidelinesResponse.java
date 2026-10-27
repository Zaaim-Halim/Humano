package com.humano.dto.hr.workflow.responses;

import java.math.BigDecimal;
import java.util.List;

/**
 * The merit matrix, rating 1 to 5. Guidance only: proposals outside it are flagged, not refused.
 */
public record MeritGuidelinesResponse(List<Band> bands) {
    public record Band(int rating, BigDecimal minPercent, BigDecimal maxPercent) {}
}
