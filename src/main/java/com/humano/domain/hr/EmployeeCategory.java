package com.humano.domain.hr;

import com.humano.domain.shared.AbstractReferenceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Tenant-configurable employment category (e.g. STAFF, MANAGER, EXECUTIVE).
 */
@Entity
@Table(name = "employee_category")
public class EmployeeCategory extends AbstractReferenceData {}
