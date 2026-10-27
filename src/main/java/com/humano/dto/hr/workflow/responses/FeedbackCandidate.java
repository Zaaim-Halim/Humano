package com.humano.dto.hr.workflow.responses;

import java.util.UUID;

/**
 * A colleague who can be asked for feedback on a review: directory details only.
 */
public record FeedbackCandidate(UUID id, String name, String jobTitle, String departmentName) {}
