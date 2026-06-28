package com.humano.domain.hr;

import com.humano.domain.shared.AbstractReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Tenant-configurable job grade / pay band (e.g. G1, G2, G3).
 */
@Entity
@Table(name = "job_grade")
public class JobGrade extends AbstractReferenceData {}
