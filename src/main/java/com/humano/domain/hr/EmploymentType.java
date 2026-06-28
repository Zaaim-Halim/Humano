package com.humano.domain.hr;

import com.humano.domain.shared.AbstractReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Tenant-configurable employment type (e.g. FULL_TIME, PART_TIME, CONTRACTOR).
 */
@Entity
@Table(name = "employment_type")
public class EmploymentType extends AbstractReferenceData {}
