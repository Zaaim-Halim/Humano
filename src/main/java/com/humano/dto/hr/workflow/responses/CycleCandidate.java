package com.humano.dto.hr.workflow.responses;

import java.util.UUID;

/**
 * Someone who can be added to a running cycle: directory details only.
 */
public record CycleCandidate(UUID id, String name, String jobTitle, String departmentName) {}
