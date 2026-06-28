package com.humano.domain.hr;

import com.humano.domain.shared.AbstractReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Tenant-configurable job level / seniority (e.g. JUNIOR, MID, SENIOR).
 */
@Entity
@Table(name = "job_level")
public class JobLevel extends AbstractReferenceData {}
