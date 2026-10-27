package com.humano.repository.hr.projection;

import java.util.UUID;

/** A manager's direct report: id and full name. */
public record TeamMemberRow(UUID id, String name) {}
