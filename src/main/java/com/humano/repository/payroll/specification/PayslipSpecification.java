package com.humano.repository.payroll.specification;

import com.humano.domain.payroll.PayrollPeriod;
import com.humano.domain.payroll.PayrollResult;
import com.humano.domain.payroll.PayrollRun;
import com.humano.domain.payroll.Payslip;
import com.humano.domain.shared.Employee;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * JPA Specification for filtering Payslip entities.
 * Provides comprehensive search capabilities across all Payslip attributes.
 */
public final class PayslipSpecification {

    private PayslipSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching payslips with multiple criteria.
     *
     * @param employeeId Employee ID filter
     * @param payslipNumber Payslip number filter (partial match)
     * @param payrollRunId Payroll run ID filter
     * @param minGross Minimum gross amount filter
     * @param maxGross Maximum gross amount filter
     * @param minNet Minimum net amount filter
     * @param maxNet Maximum net amount filter
     * @param periodStartFrom Period start date from filter
     * @param periodStartTo Period start date to filter
     * @param periodEndFrom Period end date from filter
     * @param periodEndTo Period end date to filter
     * @param createdBy Created by filter (partial match)
     * @param createdDateFrom Created date from filter
     * @param createdDateTo Created date to filter
     * @return Specification for the given criteria
     */
    public static Specification<Payslip> withCriteria(
        UUID employeeId,
        String payslipNumber,
        UUID payrollRunId,
        BigDecimal minGross,
        BigDecimal maxGross,
        BigDecimal minNet,
        BigDecimal maxNet,
        LocalDate periodStartFrom,
        LocalDate periodStartTo,
        LocalDate periodEndFrom,
        LocalDate periodEndTo,
        String createdBy,
        Instant createdDateFrom,
        Instant createdDateTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Join to PayrollResult to access employee and amounts
            Join<Payslip, PayrollResult> resultJoin = joinResult(root);

            // Filter by employee ID
            if (employeeId != null) {
                predicates.add(cb.equal(joinEmployee(resultJoin).get("id"), employeeId));
            }

            // Filter by payslip number (partial match, case-insensitive)
            if (StringUtils.hasText(payslipNumber)) {
                predicates.add(cb.like(cb.lower(root.get("number")), "%" + payslipNumber.toLowerCase() + "%"));
            }

            // Filter by payroll run ID
            if (payrollRunId != null) {
                predicates.add(cb.equal(joinRun(resultJoin).get("id"), payrollRunId));
            }

            // Filter by minimum gross amount
            if (minGross != null) {
                predicates.add(cb.greaterThanOrEqualTo(resultJoin.get("gross"), minGross));
            }

            // Filter by maximum gross amount
            if (maxGross != null) {
                predicates.add(cb.lessThanOrEqualTo(resultJoin.get("gross"), maxGross));
            }

            // Filter by minimum net amount
            if (minNet != null) {
                predicates.add(cb.greaterThanOrEqualTo(resultJoin.get("net"), minNet));
            }

            // Filter by maximum net amount
            if (maxNet != null) {
                predicates.add(cb.lessThanOrEqualTo(resultJoin.get("net"), maxNet));
            }

            // Filter by period date ranges
            if (periodStartFrom != null || periodStartTo != null || periodEndFrom != null || periodEndTo != null) {
                Join<PayrollRun, PayrollPeriod> periodJoin = joinPeriod(joinRun(resultJoin));
                addPeriodDatePredicates(cb, predicates, periodJoin, periodStartFrom, periodStartTo, periodEndFrom, periodEndTo);
            }

            // Filter by created by (partial match, case-insensitive)
            if (StringUtils.hasText(createdBy)) {
                predicates.add(cb.like(cb.lower(root.get("createdBy")), "%" + createdBy.toLowerCase() + "%"));
            }

            // Filter by created date from
            if (createdDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdDate"), createdDateFrom));
            }

            // Filter by created date to
            if (createdDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdDate"), createdDateTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Joins the PayrollResult entity from Payslip.
     */
    private static Join<Payslip, PayrollResult> joinResult(Root<Payslip> root) {
        return root.join("result");
    }

    /**
     * Joins the Employee entity from PayrollResult.
     */
    private static Join<PayrollResult, Employee> joinEmployee(Join<Payslip, PayrollResult> resultJoin) {
        return resultJoin.join("employee");
    }

    /**
     * Joins the PayrollRun entity from PayrollResult.
     */
    private static Join<PayrollResult, PayrollRun> joinRun(Join<Payslip, PayrollResult> resultJoin) {
        return resultJoin.join("run");
    }

    /**
     * Joins the PayrollPeriod entity from PayrollRun.
     */
    private static Join<PayrollRun, PayrollPeriod> joinPeriod(Join<PayrollResult, PayrollRun> runJoin) {
        return runJoin.join("period");
    }

    /**
     * Adds predicates for period date filtering.
     */
    private static void addPeriodDatePredicates(
        jakarta.persistence.criteria.CriteriaBuilder cb,
        List<Predicate> predicates,
        Join<PayrollRun, PayrollPeriod> periodJoin,
        LocalDate periodStartFrom,
        LocalDate periodStartTo,
        LocalDate periodEndFrom,
        LocalDate periodEndTo
    ) {
        if (periodStartFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(periodJoin.get("startDate"), periodStartFrom));
        }
        if (periodStartTo != null) {
            predicates.add(cb.lessThanOrEqualTo(periodJoin.get("startDate"), periodStartTo));
        }
        if (periodEndFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(periodJoin.get("endDate"), periodEndFrom));
        }
        if (periodEndTo != null) {
            predicates.add(cb.lessThanOrEqualTo(periodJoin.get("endDate"), periodEndTo));
        }
    }
}
