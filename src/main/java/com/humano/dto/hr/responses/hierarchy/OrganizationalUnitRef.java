package com.humano.dto.hr.responses.hierarchy;

import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import java.util.UUID;

/**
 * Minimal embedded reference to an organizational unit — id, display name, type
 * and the materialized path so the frontend can resolve the unit's location in
 * the org chart without an extra lookup.
 */
public record OrganizationalUnitRef(UUID id, String name, OrganizationalUnitType type, String path) {}
