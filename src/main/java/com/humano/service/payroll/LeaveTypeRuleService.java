package com.humano.service.payroll;

import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.domain.payroll.LeavePayBand;
import com.humano.domain.payroll.LeaveTypeRule;
import com.humano.domain.payroll.PayComponent;
import com.humano.domain.shared.Country;
import com.humano.dto.payroll.request.CreateLeaveTypeRuleRequest;
import com.humano.dto.payroll.request.UpdateLeaveTypeRuleRequest;
import com.humano.dto.payroll.response.LeaveTypeRuleResponse;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.payroll.LeaveTypeRuleRepository;
import com.humano.repository.payroll.PayComponentRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.payroll.template.PayrollTemplateService;
import com.humano.service.payroll.template.model.PayrollTemplate;
import java.math.BigDecimal;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing leave type rules that define how different types of leave
 * affect payroll calculations.
 * <p>
 * Payroll deducts approved leave by these rules (see {@code PayrollProcessingService} step 3); without
 * a rule, leave of that type is paid in full.
 */
@Service
@Transactional("tenantTransactionManager")
public class LeaveTypeRuleService {

    private static final Logger log = LoggerFactory.getLogger(LeaveTypeRuleService.class);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final LeaveTypeRuleRepository ruleRepository;
    private final CountryRepository countryRepository;
    private final PayComponentRepository payComponentRepository;
    private final PayrollTemplateService payrollTemplateService;

    public LeaveTypeRuleService(
        LeaveTypeRuleRepository ruleRepository,
        CountryRepository countryRepository,
        PayComponentRepository payComponentRepository,
        PayrollTemplateService payrollTemplateService
    ) {
        this.ruleRepository = ruleRepository;
        this.countryRepository = countryRepository;
        this.payComponentRepository = payComponentRepository;
        this.payrollTemplateService = payrollTemplateService;
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
        rule.setDeductionPercentage(checkedPercentage(request.deductionPercentage()));
        rule.setPayBands(checkedBands(request.payBands()));
        // Pay not earned is normally neither taxed nor contributed on; deducting it from net pay is the exception.
        rule.setAffectsTaxableSalary(request.affectsTaxableSalary() == null || request.affectsTaxableSalary());

        ensureDeductionComponent();
        rule = ruleRepository.save(rule);
        log.info("Created leave type rule for {} in {}", request.leaveType(), country.getName());

        return toResponse(rule);
    }

    /**
     * Updates a leave type rule; null fields keep their value.
     */
    public LeaveTypeRuleResponse updateRule(UUID ruleId, UpdateLeaveTypeRuleRequest request) {
        LeaveTypeRule rule = ruleRepository.findById(ruleId).orElseThrow(() -> new EntityNotFoundException("LeaveTypeRule", ruleId));

        if (request.deductionPercentage() != null) {
            rule.setDeductionPercentage(checkedPercentage(request.deductionPercentage()));
        }

        if (request.payBands() != null) {
            rule.setPayBands(checkedBands(request.payBands()));
        }

        if (request.affectsTaxableSalary() != null) {
            rule.setAffectsTaxableSalary(request.affectsTaxableSalary());
        }

        ensureDeductionComponent();
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
    @Transactional(value = "tenantTransactionManager", readOnly = true)
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
    @Transactional(value = "tenantTransactionManager", readOnly = true)
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
     * The leave pay rules payroll applies: the workspace's own, and the statutory rules of the enabled payroll
     * templates for the country and leave types the workspace has no rule for. By country, then leave type.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<LeaveTypeRuleResponse> getAllRules() {
        List<LeaveTypeRule> own = ruleRepository.findAll();
        Map<String, Country> countriesByCode = new HashMap<>();
        countryRepository.findAll().forEach(c -> countriesByCode.put(c.getCode().name(), c));
        Set<String> statutoryKeys = new HashSet<>();
        List<LeaveTypeRuleResponse> rules = new ArrayList<>();
        for (PayrollTemplateService.StatutoryLeavePay statutory : payrollTemplateService.statutoryLeavePay()) {
            Country country = countriesByCode.get(statutory.template().jurisdiction().country());
            if (country == null) continue;
            String key = country.getId() + "/" + statutory.rule().leaveType();
            statutoryKeys.add(key);
            boolean overridden = own
                .stream()
                .anyMatch(r -> r.getCountry().getId().equals(country.getId()) && r.getLeaveType() == statutory.rule().leaveType());
            if (!overridden) rules.add(toResponse(statutory, country));
        }
        for (LeaveTypeRule rule : own) {
            rules.add(toResponse(rule, statutoryKeys.contains(rule.getCountry().getId() + "/" + rule.getLeaveType())));
        }
        return rules
            .stream()
            .sorted(Comparator.comparing(LeaveTypeRuleResponse::countryName).thenComparing(LeaveTypeRuleResponse::leaveType))
            .toList();
    }

    private static BigDecimal checkedPercentage(BigDecimal percentage) {
        if (percentage == null || percentage.signum() < 0 || percentage.compareTo(HUNDRED) > 0) {
            throw new BusinessRuleViolationException("Deduction percentage must be between 0 and 100");
        }
        return percentage;
    }

    /** Bands in day order, each with a valid percentage, not overlapping; only the last may be open-ended. */
    private static List<LeavePayBand> checkedBands(List<LeavePayBand> bands) {
        if (bands == null || bands.isEmpty()) {
            return List.of();
        }
        List<LeavePayBand> sorted = bands.stream().sorted(Comparator.comparingInt(LeavePayBand::fromDay)).toList();
        int nextFree = 1;
        for (int i = 0; i < sorted.size(); i++) {
            LeavePayBand band = sorted.get(i);
            if (band.fromDay() < 1) {
                throw new BusinessRuleViolationException("A pay band starts on day 1 of the absence or later");
            }
            if (band.toDay() != null && band.toDay() < band.fromDay()) {
                throw new BusinessRuleViolationException("A pay band can't end before day " + band.fromDay() + ", where it starts");
            }
            if (band.toDay() == null && i < sorted.size() - 1) {
                throw new BusinessRuleViolationException("Only the last pay band can run to the end of the absence");
            }
            if (band.fromDay() < nextFree) {
                throw new BusinessRuleViolationException("Pay bands overlap on day " + band.fromDay() + " of the absence");
            }
            checkedPercentage(band.deductionPercentage());
            nextFree = band.toDay() == null ? Integer.MAX_VALUE : band.toDay() + 1;
        }
        return sorted;
    }

    /** Payroll puts leave deductions on the DEDUCTION pay component; a workspace may not have it yet. */
    private void ensureDeductionComponent() {
        boolean exists = payComponentRepository.findAll().stream().anyMatch(c -> c.getCode() == PayComponentCode.DEDUCTION);
        if (!exists) {
            PayComponent component = new PayComponent();
            component.setCode(PayComponentCode.DEDUCTION);
            component.setName("Deductions");
            component.setKind(Kind.DEDUCTION);
            component.setMeasure(Measurement.AMOUNT);
            component.setTaxable(false);
            component.setContributesToSocial(false);
            payComponentRepository.save(component);
            log.info("Created pay component {} for leave deductions", PayComponentCode.DEDUCTION);
        }
    }

    private LeaveTypeRuleResponse toResponse(LeaveTypeRule rule) {
        return toResponse(rule, false);
    }

    private LeaveTypeRuleResponse toResponse(LeaveTypeRule rule, boolean overridesTemplate) {
        Country country = rule.getCountry();
        return new LeaveTypeRuleResponse(
            rule.getId(),
            country.getId(),
            country.getCode() != null ? country.getCode().name() : null,
            country.getName(),
            rule.getLeaveType(),
            rule.getLeaveType().name().replace("_", " "),
            rule.getDeductionPercentage(),
            rule.getPayBands(),
            Boolean.TRUE.equals(rule.getAffectsTaxableSalary()),
            describe(rule.getLeaveType(), rule.getPayBands(), rule.getDeductionPercentage()),
            "WORKSPACE",
            null,
            null,
            null,
            null,
            null,
            null,
            overridesTemplate
        );
    }

    private static LeaveTypeRuleResponse toResponse(PayrollTemplateService.StatutoryLeavePay statutory, Country country) {
        PayrollTemplate template = statutory.template();
        PayrollTemplate.LeavePay rule = statutory.rule();
        List<LeavePayBand> bands = rule.payBands() == null ? List.of() : rule.payBands();
        PayrollTemplate.Source source = template.sources() == null ? null : template.sources().get(rule.source());
        return new LeaveTypeRuleResponse(
            null,
            country.getId(),
            country.getCode().name(),
            country.getName(),
            rule.leaveType(),
            rule.leaveType().name().replace("_", " "),
            rule.deductionPercentage(),
            bands,
            true,
            describe(rule.leaveType(), bands, rule.deductionPercentage()),
            "TEMPLATE",
            template.id(),
            template.name(),
            template.version(),
            rule.note(),
            source == null ? null : source.title(),
            source == null ? null : source.url(),
            false
        );
    }

    private static String describe(LeaveType type, List<LeavePayBand> bands, BigDecimal deductionPercentage) {
        Integer lastBandedDay = bands.isEmpty() ? null : bands.get(bands.size() - 1).toDay();
        String description = type.name().replace("_", " ") + " - ";
        if (!bands.isEmpty() && lastBandedDay == null) {
            return description + "by pay band";
        }
        return description + (bands.isEmpty() ? "" : "by pay band to day " + lastBandedDay + ", then ") + describe(deductionPercentage);
    }

    private static String describe(BigDecimal deductionPercentage) {
        return deductionPercentage.signum() == 0
            ? "Fully paid"
            : deductionPercentage.compareTo(HUNDRED) == 0 ? "Unpaid" : deductionPercentage + "% deduction";
    }
}
