package com.humano.domain.hr;

import com.humano.domain.shared.AbstractReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Tenant-configurable marital status (e.g. SINGLE, MARRIED, DIVORCED).
 */
@Entity
@Table(name = "marital_status")
public class MaritalStatus extends AbstractReferenceData {}
