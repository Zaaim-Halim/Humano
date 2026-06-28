package com.humano.domain.hr;

import com.humano.domain.shared.AbstractReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Tenant-configurable termination reason (e.g. RESIGNATION, RETIREMENT, REDUNDANCY).
 */
@Entity
@Table(name = "termination_reason")
public class TerminationReason extends AbstractReferenceData {}
