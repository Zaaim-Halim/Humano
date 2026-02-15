/**
 * JPA Specifications for payroll entities.
 * <p>
 * This package contains specification classes that provide dynamic query building
 * capabilities for payroll entities using the JPA Criteria API.
 * <p>
 * Each specification class provides a static factory method that builds a
 * Specification based on the provided search criteria. All criteria are optional
 * and combined with AND logic.
 * <p>
 * Supported entities:
 * <ul>
 *   <li>{@link com.humano.repository.payroll.specification.CompensationSpecification} - Search compensations</li>
 *   <li>{@link com.humano.repository.payroll.specification.BonusSpecification} - Search bonuses</li>
 *   <li>{@link com.humano.repository.payroll.specification.DeductionSpecification} - Search deductions</li>
 *   <li>{@link com.humano.repository.payroll.specification.PayslipSpecification} - Search payslips</li>
 * </ul>
 */
package com.humano.repository.payroll.specification;
