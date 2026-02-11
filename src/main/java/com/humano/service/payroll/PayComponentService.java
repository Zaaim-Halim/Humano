package com.humano.service.payroll;

import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.domain.payroll.PayComponent;
import com.humano.domain.payroll.PayRule;
import com.humano.dto.payroll.request.CreatePayComponentRequest;
import com.humano.dto.payroll.request.CreatePayRuleRequest;
import com.humano.dto.payroll.response.PayComponentResponse;
import com.humano.repository.payroll.PayComponentRepository;
import com.humano.repository.payroll.PayRuleRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing pay components and pay rules.
 * Handles the configuration of payroll calculation components and their formulas.
 */
@Service
@Transactional
public class PayComponentService {

    private static final Logger log = LoggerFactory.getLogger(PayComponentService.class);

    private final PayComponentRepository componentRepository;
    private final PayRuleRepository ruleRepository;
    private final PayrollFormulaEngine formulaEngine;

    public PayComponentService(
        PayComponentRepository componentRepository,
        PayRuleRepository ruleRepository,
        PayrollFormulaEngine formulaEngine
    ) {
        this.componentRepository = componentRepository;
        this.ruleRepository = ruleRepository;
        this.formulaEngine = formulaEngine;
    }

    /**
     * Creates a new pay component.
     */
    public PayComponentResponse createComponent(CreatePayComponentRequest request) {
        log.debug("Creating pay component: {}", request.code());

        // Check for duplicate code
        boolean exists = componentRepository.exists(
            (Specification<PayComponent>) (root, query, cb) -> cb.equal(root.get("code"), request.code())
        );

        if (exists) {
            throw new BusinessRuleViolationException("Pay component with code " + request.code() + " already exists");
        }

        PayComponent component = new PayComponent();
        component.setCode(request.code());
        component.setName(request.name());
        component.setKind(request.kind());
        component.setMeasure(request.measure());
        component.setTaxable(request.taxable());
        component.setContributesToSocial(request.contributesToSocial());
        component.setPercentage(request.percentage());
        component.setCalcPhase(request.calcPhase());

        component = componentRepository.save(component);
        log.info("Created pay component {} ({})", component.getCode(), component.getId());

        return toResponse(component);
    }

    /**
     * Creates a pay rule for a component.
     */
    public PayComponentResponse createRule(CreatePayRuleRequest request) {
        log.debug("Creating pay rule for component: {}", request.payComponentId());

        PayComponent component = componentRepository
            .findById(request.payComponentId())
            .orElseThrow(() -> new EntityNotFoundException("PayComponent", request.payComponentId()));

        // Validate formula syntax
        validateFormula(request.formula());

        PayRule rule = new PayRule();
        rule.setPayComponent(component);
        rule.setFormula(request.formula());
        rule.setEffectiveFrom(request.effectiveFrom());
        rule.setEffectiveTo(request.effectiveTo());
        rule.setPriority(request.priority());
        rule.setBaseFormulaRef(request.baseFormulaRef());
        rule.setActive(request.active());

        ruleRepository.save(rule);
        log.info("Created pay rule for component {}", component.getCode());

        return toResponse(component);
    }

    /**
     * Gets all pay components with pagination.
     */
    @Transactional(readOnly = true)
    public Page<PayComponentResponse> getAllComponents(Pageable pageable) {
        return componentRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Gets pay components by kind (EARNING, DEDUCTION, EMPLOYER_CHARGE).
     */
    @Transactional(readOnly = true)
    public List<PayComponentResponse> getComponentsByKind(Kind kind) {
        return componentRepository
            .findAll(
                (Specification<PayComponent>) (root, query, cb) -> {
                    query.orderBy(cb.asc(root.get("calcPhase")), cb.asc(root.get("name")));
                    return cb.equal(root.get("kind"), kind);
                }
            )
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Gets a pay component by its code.
     */
    @Transactional(readOnly = true)
    public PayComponentResponse getComponentByCode(PayComponentCode code) {
        PayComponent component = componentRepository
            .findAll((Specification<PayComponent>) (root, query, cb) -> cb.equal(root.get("code"), code))
            .stream()
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("PayComponent with code " + code));

        return toResponse(component);
    }

    /**
     * Updates a pay component.
     */
    public PayComponentResponse updateComponent(
        UUID componentId,
        String name,
        Boolean taxable,
        Boolean contributesToSocial,
        Integer calcPhase
    ) {
        PayComponent component = componentRepository
            .findById(componentId)
            .orElseThrow(() -> new EntityNotFoundException("PayComponent", componentId));

        if (name != null) component.setName(name);
        if (taxable != null) component.setTaxable(taxable);
        if (contributesToSocial != null) component.setContributesToSocial(contributesToSocial);
        if (calcPhase != null) component.setCalcPhase(calcPhase);

        component = componentRepository.save(component);
        log.info("Updated pay component {}", componentId);

        return toResponse(component);
    }

    /**
     * Updates a pay rule's formula.
     */
    public PayComponentResponse updateRuleFormula(UUID ruleId, String newFormula) {
        PayRule rule = ruleRepository.findById(ruleId).orElseThrow(() -> new EntityNotFoundException("PayRule", ruleId));

        validateFormula(newFormula);

        rule.setFormula(newFormula);
        rule = ruleRepository.save(rule);

        log.info("Updated formula for rule {}", ruleId);
        return toResponse(rule.getPayComponent());
    }

    /**
     * Activates or deactivates a pay rule.
     */
    public PayComponentResponse setRuleActive(UUID ruleId, boolean active) {
        PayRule rule = ruleRepository.findById(ruleId).orElseThrow(() -> new EntityNotFoundException("PayRule", ruleId));

        rule.setActive(active);
        rule = ruleRepository.save(rule);

        log.info("{} pay rule {}", active ? "Activated" : "Deactivated", ruleId);
        return toResponse(rule.getPayComponent());
    }

    /**
     * Gets active rules for a component on a specific date.
     */
    @Transactional(readOnly = true)
    public List<PayComponentResponse.PayRuleSummary> getActiveRules(UUID componentId, LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

        return ruleRepository
            .findAll(
                (Specification<PayRule>) (root, query, cb) -> {
                    query.orderBy(cb.desc(root.get("priority")));
                    return cb.and(
                        cb.equal(root.get("payComponent").get("id"), componentId),
                        cb.isTrue(root.get("active")),
                        cb.or(cb.isNull(root.get("effectiveFrom")), cb.lessThanOrEqualTo(root.get("effectiveFrom"), effectiveDate)),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), effectiveDate))
                    );
                }
            )
            .stream()
            .map(r -> new PayComponentResponse.PayRuleSummary(r.getId(), r.getFormula(), r.getPriority(), r.getActive()))
            .toList();
    }

    /**
     * Gets the calculation order for all components.
     */
    @Transactional(readOnly = true)
    public List<PayComponentResponse> getCalculationOrder() {
        return componentRepository
            .findAll(
                (Specification<PayComponent>) (root, query, cb) -> {
                    query.orderBy(cb.asc(root.get("calcPhase")), cb.asc(root.get("kind")));
                    return cb.conjunction();
                }
            )
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Validates a formula without actually executing it.
     */
    public Map<String, Object> validateFormula(String formula) {
        Map<String, Object> result = new HashMap<>();
        result.put("formula", formula);

        try {
            // Try to parse the formula with sample variables
            Map<String, Object> sampleContext = new HashMap<>();
            sampleContext.put("baseSalary", java.math.BigDecimal.valueOf(5000));
            sampleContext.put("grossSalary", java.math.BigDecimal.valueOf(5000));
            sampleContext.put("workDays", 22);
            sampleContext.put("OT_HOURS", java.math.BigDecimal.valueOf(10));
            sampleContext.put("OT_RATE", java.math.BigDecimal.valueOf(1.5));

            BigDecimal testResult = formulaEngine.evaluateFormula(formula, sampleContext, BigDecimal.class);

            result.put("valid", true);
            result.put("testResult", testResult);
            result.put("resultType", testResult != null ? testResult.getClass().getSimpleName() : "null");
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
            throw new BusinessRuleViolationException("Invalid formula: " + e.getMessage());
        }

        return result;
    }

    /**
     * Copies rules from one component to another.
     */
    public PayComponentResponse copyRules(UUID sourceComponentId, UUID targetComponentId) {
        PayComponent source = componentRepository
            .findById(sourceComponentId)
            .orElseThrow(() -> new EntityNotFoundException("PayComponent (source)", sourceComponentId));

        PayComponent target = componentRepository
            .findById(targetComponentId)
            .orElseThrow(() -> new EntityNotFoundException("PayComponent (target)", targetComponentId));

        List<PayRule> sourceRules = ruleRepository.findAll(
            (Specification<PayRule>) (root, query, cb) -> cb.equal(root.get("payComponent").get("id"), sourceComponentId)
        );

        for (PayRule sourceRule : sourceRules) {
            PayRule newRule = new PayRule();
            newRule.setPayComponent(target);
            newRule.setFormula(sourceRule.getFormula());
            newRule.setEffectiveFrom(sourceRule.getEffectiveFrom());
            newRule.setEffectiveTo(sourceRule.getEffectiveTo());
            newRule.setPriority(sourceRule.getPriority());
            newRule.setBaseFormulaRef(sourceRule.getBaseFormulaRef());
            newRule.setActive(false); // Start as inactive
            ruleRepository.save(newRule);
        }

        log.info("Copied {} rules from component {} to {}", sourceRules.size(), sourceComponentId, targetComponentId);
        return toResponse(target);
    }

    /**
     * Gets component usage statistics.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getComponentStatistics() {
        List<PayComponent> components = componentRepository.findAll();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalComponents", components.size());
        stats.put("byKind", components.stream().collect(Collectors.groupingBy(c -> c.getKind().name(), Collectors.counting())));
        stats.put("taxableCount", components.stream().filter(PayComponent::getTaxable).count());
        stats.put("socialContributingCount", components.stream().filter(PayComponent::getContributesToSocial).count());

        // Count active rules
        long activeRuleCount = ruleRepository.count((Specification<PayRule>) (root, query, cb) -> cb.isTrue(root.get("active")));
        stats.put("activeRuleCount", activeRuleCount);

        return stats;
    }

    private PayComponentResponse toResponse(PayComponent component) {
        List<PayRule> activeRules = ruleRepository.findAll(
            (Specification<PayRule>) (root, query, cb) -> {
                query.orderBy(cb.desc(root.get("priority")));
                return cb.and(cb.equal(root.get("payComponent").get("id"), component.getId()), cb.isTrue(root.get("active")));
            }
        );

        return new PayComponentResponse(
            component.getId(),
            component.getCode(),
            component.getName(),
            component.getKind(),
            component.getMeasure(),
            component.getTaxable(),
            component.getContributesToSocial(),
            component.getPercentage(),
            component.getCalcPhase(),
            activeRules.size(),
            activeRules
                .stream()
                .map(r -> new PayComponentResponse.PayRuleSummary(r.getId(), r.getFormula(), r.getPriority(), r.getActive()))
                .toList()
        );
    }
}
