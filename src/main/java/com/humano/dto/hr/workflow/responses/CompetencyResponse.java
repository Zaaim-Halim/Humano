package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.CompetencyCategory;
import java.util.List;
import java.util.UUID;

/**
 * A competency in the performance framework. {@code anchors} describe rating levels 1 to 5 (blank
 * levels are {@code null}); {@code usedBy} names the templates using it.
 */
public record CompetencyResponse(
    UUID id,
    String name,
    String description,
    CompetencyCategory category,
    List<String> anchors,
    boolean active,
    List<String> usedBy
) {}
