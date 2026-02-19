package com.humano.service.payroll;

import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.payroll.LeaveTypeRule;
import com.humano.domain.shared.Country;
import com.humano.dto.payroll.request.CreateLeaveTypeRuleRequest;
import com.humano.dto.payroll.response.LeaveTypeRuleResponse;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.payroll.LeaveTypeRuleRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing leave type rules that define how different types of leave
 * affect payroll calculations.
 */
@Service
@Transactional
public class LeaveTypeRuleService {

    private static final Logger log = LoggerFactory.getLogger(LeaveTypeRuleService.class);

    private final LeaveTypeRuleRepository ruleRepository;
    private final CountryRepository countryRepository;

    public LeaveTypeRuleService(LeaveTypeRuleRepository ruleRepository, CountryRepository countryRepository) {
        this.ruleRepository = ruleRepository;
        this.countryRepository = countryRepository;
    }

    /**
     * Creates a new leave type rule.
     */
    public LeaveTypeRuleResponse createRule(CreateLeaveTypeRuleRequest request) {
        log.debug("Creating leave type rule for {} in country {}", request.leaveType(), request.countryId());

        Country country = countryRepository
            .findById(request.countryId())
            .orElseThrow(() -> new EntityNotFoundException("Country", request.countryId()));

        boolean exists = ruleRepository.exists(
            (Specification<LeaveTypeRule>) (root, query, cb) ->
                cb.and(cb.equal(root.get("country").get("id"), request.countryId()), cb.equal(root.get("leaveType"), request.leaveType()))
        );

        if (exists) {
            throw new BusinessRuleViolationException(
                "Leave type rule already exists for " + request.leaveType() + " in " + country.getName()
            );
        }

        LeaveTypeRule rule = new LeaveTypeRule();
        rule.setCountry(country);
        rule.setLeaveType(request.leaveType());
        rule.setDeductionPercentage(request.deductionPercentage());
        rule.setAffectsTaxableSalary(request.affectsTaxableSalary());

        rule = ruleRepository.save(rule);
        log.info("Created leave type rule for {} in {}", request.leaveType(), country.getName());

        return toResponse(rule);
    }

    /**
     * Updates a leave type rule.
     */
    public LeaveTypeRuleResponse updateRule(UUID ruleId, BigDecimal deductionPercentage, Boolean affectsTaxableSalary) {
        LeaveTypeRule rule = ruleRepository.findById(ruleId).orElseThrow(() -> new EntityNotFoundException("LeaveTypeRule", ruleId));

        if (deductionPercentage != null) {
            if (deductionPercentage.compareTo(BigDecimal.ZERO) < 0 || deductionPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessRuleViolationException("Deduction percentage must be between 0 and 100");
            }
            rule.setDeductionPercentage(deductionPercentage);
        }

        if (affectsTaxableSalary != null) {
            rule.setAffectsTaxableSalary(affectsTaxableSalary);
        }

        rule = ruleRepository.save(rule);
        log.info("Updated leave type rule {}", ruleId);

        return toResponse(rule);
    }

    /**
     * Deletes a leave type rule.
     */
    public void deleteRule(UUID ruleId) {
        LeaveTypeRule rule = ruleRepository.findById(ruleId).orElseThrow(() -> new EntityNotFoundException("LeaveTypeRule", ruleId));

        ruleRepository.delete(rule);
        log.info("Deleted leave type rule {} for {} in {}", ruleId, rule.getLeaveType(), rule.getCountry().getName());
    }

    /**
     * Gets all leave type rules for a country.
     */
    @Transactional(readOnly = true)
    public List<LeaveTypeRuleResponse> getRulesForCountry(UUID countryId) {
        return ruleRepository
            .findAll(
                (Specification<LeaveTypeRule>) (root, query, cb) -> {
                    if (query != null) query.orderBy(cb.asc(root.get("leaveType")));
                    return cb.equal(root.get("country").get("id"), countryId);
                }
            )
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Gets a specific leave type rule for a country and leave type.
     */
    @Transactional(readOnly = true)
    public Optional<LeaveTypeRuleResponse> getRule(UUID countryId, LeaveType leaveType) {
        return ruleRepository
            .findAll(
                (Specification<LeaveTypeRule>) (root, query, cb) ->
                    cb.and(cb.equal(root.get("country").get("id"), countryId), cb.equal(root.get("leaveType"), leaveType))
            )
            .stream()
            .findFirst()
            .map(this::toResponse);
    }

    /**
     * Calculates leave deduction for payroll.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> calculateLeaveDeduction(UUID countryId, LeaveType leaveType, BigDecimal dailySalary, int leaveDays) {
        LeaveTypeRule rule = ruleRepository
            .findAll(
                (Specification<LeaveTypeRule>) (root, query, cb) ->
                    cb.and(cb.equal(root.get("country").get("id"), countryId), cb.equal(root.get("leaveType"), leaveType))
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessRuleViolationException("No leave type rule found for " + leaveType + " in country " + countryId)
            );

        BigDecimal totalDailySalary = dailySalary.multiply(BigDecimal.valueOf(leaveDays));
        BigDecimal deductionAmount = totalDailySalary
            .multiply(rule.getDeductionPercentage())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Map<String, Object> result = new HashMap<>();
        result.put("leaveType", leaveType.name());
        result.put("leaveDays", leaveDays);
        result.put("dailySalary", dailySalary);
        result.put("totalDailySalary", totalDailySalary);
        result.put("deductionPercentage", rule.getDeductionPercentage());
        result.put("deductionAmount", deductionAmount);
        result.put("paidAmount", totalDailySalary.subtract(deductionAmount));
        result.put("affectsTaxableSalary", rule.getAffectsTaxableSalary());

        return result;
    }

    /**
     * Gets all leave type rules across all countries.
     */
    @Transactional(readOnly = true)
    public List<LeaveTypeRuleResponse> getAllRules() {
        return ruleRepository.findAll().stream().map(this::toResponse).toList();
    }

    private LeaveTypeRuleResponse toResponse(LeaveTypeRule rule) {
        Country country = rule.getCountry();
        String description =
            rule.getLeaveType().name().replace("_", " ") +
            " - " +
            (rule.getDeductionPercentage().compareTo(BigDecimal.ZERO) == 0
                    ? "Fully paid"
                    : rule.getDeductionPercentage().compareTo(BigDecimal.valueOf(100)) == 0
                        ? "Unpaid"
                        : rule.getDeductionPercentage() + "% deduction");

        return new LeaveTypeRuleResponse(
            rule.getId(),
            country.getId(),
            country.getName(),
            rule.getLeaveType(),
            rule.getLeaveType().name().replace("_", " "),
            rule.getDeductionPercentage(),
            rule.getAffectsTaxableSalary(),
            description
        );
    }
}
