package com.humano.service.payroll.template;

import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.domain.enumeration.payroll.PayrollLineCategory;
import com.humano.domain.enumeration.payroll.TaxType;
import com.humano.domain.payroll.EmployeeTemplateProfile;
import com.humano.domain.payroll.PayComponent;
import com.humano.domain.payroll.PayrollCalendar;
import com.humano.domain.payroll.PayrollTemplateAdoption;
import com.humano.domain.payroll.TaxBracket;
import com.humano.domain.payroll.TaxWithholding;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.ConfigurePayrollTemplateRequest;
import com.humano.dto.payroll.request.UpdateTemplateProfilesRequest;
import com.humano.dto.payroll.response.EmployeeTemplateProfileResponse;
import com.humano.dto.payroll.response.EmployeeTemplateResponse;
import com.humano.dto.payroll.response.PayrollTemplateAdoptionResponse;
import com.humano.dto.payroll.response.PayrollTemplateResponse;
import com.humano.dto.payroll.response.TemplateEmployeeResponse;
import com.humano.repository.payroll.EmployeeTemplateProfileRepository;
import com.humano.repository.payroll.PayComponentRepository;
import com.humano.repository.payroll.PayrollCalendarRepository;
import com.humano.repository.payroll.PayrollLineRepository;
import com.humano.repository.payroll.PayrollTemplateAdoptionRepository;
import com.humano.repository.payroll.TaxBracketRepository;
import com.humano.repository.payroll.TaxWithholdingRepository;
import com.humano.repository.payroll.projection.TemplateYtdRow;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.payroll.template.catalog.PayrollTemplateCatalog;
import com.humano.service.payroll.template.engine.PayrollTemplateEngine;
import com.humano.service.payroll.template.engine.TemplateCalculationException;
import com.humano.service.payroll.template.engine.TemplateInputs;
import com.humano.service.payroll.template.engine.YtdAccess;
import com.humano.service.payroll.template.model.InputField;
import com.humano.service.payroll.template.model.LineSide;
import com.humano.service.payroll.template.model.PayrollTemplate;
import com.humano.service.payroll.template.model.Step;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A tenant's use of the shipped payroll template catalog: adopting templates, employee profiles, and
 * what the payroll run needs to apply them (which templates cover an employee, year-to-date sums, and
 * a fingerprint for the run's idempotency hash).
 * <p>
 * Adoption stores a reference and the tenant's settings only; rules and values always come from the
 * catalog, so a newer shipped version applies to every adopter's next calculation.
 */
@Service
@Transactional("tenantTransactionManager")
public class PayrollTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PayrollTemplateService.class);

    /** A template that applies to an employee, with the tenant's settings and the employee's attributes. */
    public record AppliedTemplate(PayrollTemplate template, Map<String, String> settings, Map<String, String> attributes) {
        /** Tax types the template's steps produce. */
        public Set<TaxType> ledgers() {
            return ledgersOf(template);
        }
    }

    private final PayrollTemplateCatalog catalog;
    private final PayrollTemplateAdoptionRepository adoptionRepository;
    private final EmployeeTemplateProfileRepository profileRepository;
    private final EmployeeRepository employeeRepository;
    private final PayComponentRepository payComponentRepository;
    private final PayrollLineRepository payrollLineRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final TaxWithholdingRepository taxWithholdingRepository;
    private final PayrollCalendarRepository payrollCalendarRepository;

    public PayrollTemplateService(
        PayrollTemplateCatalog catalog,
        PayrollTemplateAdoptionRepository adoptionRepository,
        EmployeeTemplateProfileRepository profileRepository,
        EmployeeRepository employeeRepository,
        PayComponentRepository payComponentRepository,
        PayrollLineRepository payrollLineRepository,
        TaxBracketRepository taxBracketRepository,
        TaxWithholdingRepository taxWithholdingRepository,
        PayrollCalendarRepository payrollCalendarRepository
    ) {
        this.catalog = catalog;
        this.adoptionRepository = adoptionRepository;
        this.profileRepository = profileRepository;
        this.employeeRepository = employeeRepository;
        this.payComponentRepository = payComponentRepository;
        this.payrollLineRepository = payrollLineRepository;
        this.taxBracketRepository = taxBracketRepository;
        this.taxWithholdingRepository = taxWithholdingRepository;
        this.payrollCalendarRepository = payrollCalendarRepository;
    }

    // ---------------------------------------------------------------- catalog & adoption

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<PayrollTemplateResponse> list() {
        Map<String, PayrollTemplateAdoption> adoptions = adoptionRepository
            .findAll()
            .stream()
            .collect(Collectors.toMap(PayrollTemplateAdoption::getTemplateId, a -> a));
        return catalog.templates().stream().map(t -> toResponse(t, adoptions.get(t.id()))).toList();
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public PayrollTemplateResponse get(String templateId) {
        PayrollTemplate template = template(templateId);
        return toResponse(template, adoptionRepository.findByTemplateId(templateId).orElse(null));
    }

    /** Templates in this build that failed validation or their test cases, with the problems (for administrators). */
    public List<PayrollTemplateCatalog.Rejected> rejected() {
        return catalog.rejected();
    }

    /**
     * Enables or disables a template and saves its employer settings. Enabling refuses drafts,
     * sub-national templates (they need employee work locations, not yet recorded), a second enabled
     * template for the same jurisdiction, and settings the template doesn't accept.
     */
    public PayrollTemplateAdoptionResponse configure(String templateId, ConfigurePayrollTemplateRequest request) {
        PayrollTemplate template = template(templateId);
        Map<String, String> settings = clean(request.settings());
        boolean enable = Boolean.TRUE.equals(request.enabled());
        try {
            TemplateInputs.coerce(template.inputs().employer(), settings, "employer setting", enable);
        } catch (TemplateCalculationException e) {
            throw new BusinessRuleViolationException(e.getMessage());
        }
        if (enable) {
            if (!PayrollTemplateCatalog.adoptable(template)) {
                throw new BusinessRuleViolationException("Template " + template.ref() + " is a draft and can't be enabled");
            }
            if (template.jurisdiction().subdivision() != null) {
                throw new BusinessRuleViolationException(
                    "State/municipal templates need employees' work locations, which are not recorded yet; " +
                    template.id() +
                    " can't be enabled"
                );
            }
            adoptionRepository
                .findByEnabledTrue()
                .stream()
                .filter(a -> !a.getTemplateId().equals(templateId))
                .filter(
                    a ->
                        a.getCountryCode().equals(template.jurisdiction().country()) &&
                        Objects.equals(a.getSubdivision(), template.jurisdiction().subdivision())
                )
                .findFirst()
                .ifPresent(other -> {
                    throw new BusinessRuleViolationException(
                        "Template " + other.getTemplateId() + " is already enabled for " + other.getCountryCode() + "; disable it first"
                    );
                });
            ensureComponent(PayComponentCode.TAX_PIT, "Personal income tax", Kind.DEDUCTION);
            ensureComponent(PayComponentCode.EMPLOYER_CHARGE, "Employer statutory contributions", Kind.EMPLOYER_CHARGE);
        }
        PayrollTemplateAdoption adoption = adoptionRepository.findByTemplateId(templateId).orElseGet(PayrollTemplateAdoption::new);
        if (adoption.getId() == null) {
            adoption.setTemplateId(templateId);
            adoption.setAcknowledgedVersion(template.version());
        }
        adoption.setCountryCode(template.jurisdiction().country());
        adoption.setSubdivision(template.jurisdiction().subdivision());
        adoption.setEnabled(enable);
        adoption.setSettings(settings);
        adoption = adoptionRepository.save(adoption);
        log.info("Payroll template {} {} with settings {}", template.ref(), enable ? "enabled" : "disabled", settings.keySet());
        return new PayrollTemplateAdoptionResponse(toResponse(template, adoption), enable ? warnings(template) : List.of());
    }

    /** Records that an administrator has reviewed the currently shipped version (clears "update available"). */
    public PayrollTemplateResponse acknowledge(String templateId) {
        PayrollTemplate template = template(templateId);
        PayrollTemplateAdoption adoption = adoptionRepository
            .findByTemplateId(templateId)
            .orElseThrow(() -> new BusinessRuleViolationException("Template " + templateId + " has not been adopted"));
        adoption.setAcknowledgedVersion(template.version());
        return toResponse(template, adoptionRepository.save(adoption));
    }

    // ---------------------------------------------------------------- employee profiles

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public EmployeeTemplateProfileResponse getProfile(String templateId, UUID employeeId) {
        template(templateId);
        employee(employeeId);
        Map<String, String> attributes = profileRepository
            .findByEmployeeIdAndTemplateId(employeeId, templateId)
            .map(EmployeeTemplateProfile::getAttributes)
            .orElse(Map.of());
        return new EmployeeTemplateProfileResponse(templateId, employeeId, new TreeMap<>(attributes));
    }

    /** Saves an employee's attributes; values are validated against the template, required ones are enforced at calculation. */
    public EmployeeTemplateProfileResponse saveProfile(String templateId, UUID employeeId, Map<String, String> rawAttributes) {
        PayrollTemplate template = template(templateId);
        Employee employee = employee(employeeId);
        Map<String, String> attributes = clean(rawAttributes);
        try {
            TemplateInputs.coerce(template.inputs().employee(), attributes, "employee attribute", false);
        } catch (TemplateCalculationException e) {
            throw new BusinessRuleViolationException(e.getMessage());
        }
        EmployeeTemplateProfile profile = profileRepository
            .findByEmployeeIdAndTemplateId(employeeId, templateId)
            .orElseGet(() -> {
                EmployeeTemplateProfile created = new EmployeeTemplateProfile();
                created.setEmployee(employee);
                created.setTemplateId(templateId);
                return created;
            });
        profile.setAttributes(attributes);
        profileRepository.save(profile);
        return new EmployeeTemplateProfileResponse(templateId, employeeId, new TreeMap<>(attributes));
    }

    /**
     * The employees {@code templateId} covers (see {@link #coveredEmployees}), by name, each with their
     * profile and the required attributes it still lacks.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<TemplateEmployeeResponse> employees(String templateId) {
        PayrollTemplate template = template(templateId);
        Map<UUID, Map<String, String>> profiles = profileRepository
            .findByTemplateId(templateId)
            .stream()
            .collect(Collectors.toMap(p -> p.getEmployee().getId(), EmployeeTemplateProfile::getAttributes));
        return coveredEmployees(template).stream().map(e -> employeeView(template, e, profiles.getOrDefault(e.getId(), Map.of()))).toList();
    }

    /**
     * Sets the same attributes on several covered employees' profiles, keeping their other attributes
     * and creating profiles where there are none. Every resulting profile is validated before any is
     * saved, so a bad value or an employee the template doesn't cover rejects the whole request.
     */
    public List<TemplateEmployeeResponse> updateProfiles(String templateId, UpdateTemplateProfilesRequest request) {
        PayrollTemplate template = template(templateId);
        Map<String, String> changes = clean(request.attributes());
        if (changes.isEmpty()) {
            throw new BusinessRuleViolationException("Choose at least one value to set");
        }
        Set<UUID> ids = new LinkedHashSet<>(request.employeeIds());
        Map<UUID, Employee> covered = coveredEmployees(template)
            .stream()
            .filter(e -> ids.contains(e.getId()))
            .collect(Collectors.toMap(Employee::getId, e -> e));
        if (covered.size() < ids.size()) {
            throw new BusinessRuleViolationException(
                (ids.size() - covered.size()) + " of the chosen employees are not covered by template " + templateId
            );
        }
        Map<UUID, EmployeeTemplateProfile> existing = profileRepository
            .findByTemplateId(templateId)
            .stream()
            .filter(p -> ids.contains(p.getEmployee().getId()))
            .collect(Collectors.toMap(p -> p.getEmployee().getId(), p -> p));

        List<EmployeeTemplateProfile> updated = new ArrayList<>();
        for (UUID id : ids) {
            EmployeeTemplateProfile profile = existing.computeIfAbsent(id, key -> {
                EmployeeTemplateProfile created = new EmployeeTemplateProfile();
                created.setEmployee(covered.get(key));
                created.setTemplateId(templateId);
                return created;
            });
            Map<String, String> merged = new TreeMap<>(profile.getAttributes());
            merged.putAll(changes);
            try {
                TemplateInputs.coerce(template.inputs().employee(), merged, "employee attribute", false);
            } catch (TemplateCalculationException e) {
                throw new BusinessRuleViolationException(e.getMessage());
            }
            profile.setAttributes(merged);
            updated.add(profile);
        }
        profileRepository.saveAll(updated);
        log.info("Set {} on {} employee profile(s) for payroll template {}", changes.keySet(), updated.size(), templateId);
        return updated.stream().map(p -> employeeView(template, covered.get(p.getEmployee().getId()), p.getAttributes())).toList();
    }

    /**
     * The switched-on national templates that cover {@code employeeId} (see {@link #coveredBy}), each
     * with the employee's profile and the required attributes it still lacks — for the employee's own
     * page. Empty for an employee without a country or whom no template covers.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<EmployeeTemplateResponse> templatesFor(UUID employeeId) {
        Employee employee = employee(employeeId);
        if (employee.getCountry() == null) {
            return List.of();
        }
        String country = employee.getCountry().getCode().name();
        Specification<Employee> thisEmployee = (root, query, cb) -> cb.equal(root.get("id"), employeeId);
        return adoptionRepository
            .findByEnabledTrue()
            .stream()
            .filter(a -> a.getCountryCode().equals(country) && a.getSubdivision() == null)
            .map(a -> catalog.find(a.getTemplateId()))
            .flatMap(Optional::stream)
            .filter(t -> employeeRepository.count(coveredBy(t).and(thisEmployee)) > 0)
            .sorted(Comparator.comparing(PayrollTemplate::id))
            .map(t -> {
                Map<String, String> attributes = profileRepository
                    .findByEmployeeIdAndTemplateId(employeeId, t.id())
                    .map(EmployeeTemplateProfile::getAttributes)
                    .orElse(Map.of());
                return new EmployeeTemplateResponse(t.id(), t.name(), t.jurisdiction().country(), employeeView(t, employee, attributes));
            })
            .toList();
    }

    private List<Employee> coveredEmployees(PayrollTemplate template) {
        return employeeRepository.findAll(coveredBy(template), Sort.by("lastName", "firstName"));
    }

    /**
     * Who a national template covers: employees whose country of employment is the template's and
     * whom payroll can still pay under it — working now, or left on or after the start of its rules
     * (their final pay). The one definition, for the template's employee list, the warning shown on
     * switching it on, and the employee's own page.
     */
    private static Specification<Employee> coveredBy(PayrollTemplate template) {
        CountryCode country = CountryCode.valueOf(template.jurisdiction().country());
        LocalDate rulesStart = template.validity().from();
        return (root, query, cb) ->
            cb.and(
                cb.equal(root.get("country").get("code"), country),
                cb.or(
                    root.get("status").in(EmployeeStatus.WORKING),
                    cb.and(
                        cb.equal(root.get("status"), EmployeeStatus.TERMINATED),
                        cb.greaterThanOrEqualTo(root.<LocalDate>get("endDate"), rulesStart)
                    )
                )
            );
    }

    private static TemplateEmployeeResponse employeeView(PayrollTemplate template, Employee employee, Map<String, String> attributes) {
        String name = Stream.of(employee.getFirstName(), employee.getLastName())
            .filter(part -> part != null && !part.isBlank())
            .collect(Collectors.joining(" "));
        return new TemplateEmployeeResponse(
            employee.getId(),
            name.isEmpty() ? employee.getLogin() : name,
            employee.getEmployeeNumber(),
            employee.getJobTitle(),
            employee.getDepartment() != null ? employee.getDepartment().getName() : null,
            employee.getStatus(),
            new TreeMap<>(attributes),
            missingRequired(template.inputs().employee(), attributes)
        );
    }

    /** Required attributes neither set nor defaulted: what calculation refuses ({@link TemplateInputs#missingRequired}). */
    static List<String> missingRequired(List<InputField> fields, Map<String, String> attributes) {
        return TemplateInputs.missingRequired(fields, attributes);
    }

    // ---------------------------------------------------------------- payroll run support

    /**
     * The enabled templates that cover {@code employee} (by country of employment), country-level first.
     * An enabled adoption whose template this build doesn't ship (or withheld because it failed its
     * checks) is an error, never a silent fall-back to the generic tax steps.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<AppliedTemplate> applicableTo(Employee employee) {
        if (employee.getCountry() == null) {
            return List.of();
        }
        String country = employee.getCountry().getCode().name();
        List<AppliedTemplate> applied = new ArrayList<>();
        for (PayrollTemplateAdoption adoption : adoptionRepository.findByEnabledTrue()) {
            if (!adoption.getCountryCode().equals(country) || adoption.getSubdivision() != null) {
                continue;
            }
            PayrollTemplate template = catalog
                .find(adoption.getTemplateId())
                .orElseThrow(() ->
                    new TemplateCalculationException(
                        "Payroll template " +
                        adoption.getTemplateId() +
                        " is enabled for " +
                        country +
                        " but is not available in this build" +
                        catalog
                            .rejected()
                            .stream()
                            .filter(r -> adoption.getTemplateId().equals(r.templateId()))
                            .findFirst()
                            .map(r -> ": " + r.problems())
                            .orElse("")
                    )
                );
            Map<String, String> attributes = profileRepository
                .findByEmployeeIdAndTemplateId(employee.getId(), template.id())
                .map(EmployeeTemplateProfile::getAttributes)
                .orElse(Map.of());
            applied.add(new AppliedTemplate(template, adoption.getSettings(), attributes));
        }
        applied.sort(Comparator.comparing(a -> a.template().jurisdiction().subdivision() != null));
        return applied;
    }

    /**
     * What {@code template} produced for the employee earlier in the tax year, from POSTED runs only,
     * keyed for {@link YtdAccess}. Periods are placed in the tax year by their start date, or by their
     * payment date for templates on a payment-date basis.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Map<String, BigDecimal> yearToDate(UUID employeeId, PayrollTemplate template, LocalDate periodStart, LocalDate paymentDate) {
        boolean byPaymentDate = template.effectiveDateBasis() == PayrollTemplate.DateBasis.PAYMENT_DATE;
        LocalDate basis = byPaymentDate ? paymentDate : periodStart;
        LocalDate taxYearStart = PayrollTemplateEngine.taxYearStart(template, basis);
        String refPrefix = template.id() + "@%";
        List<TemplateYtdRow> rows = byPaymentDate
            ? payrollLineRepository.sumTemplateLinesByPaymentDate(employeeId, refPrefix, taxYearStart, basis)
            : payrollLineRepository.sumTemplateLines(employeeId, refPrefix, taxYearStart, basis);
        Map<String, BigDecimal> sums = new HashMap<>();
        for (TemplateYtdRow row : rows) {
            LineSide side = sideOf(row.category());
            if (side == null) continue;
            sums.merge(YtdAccess.key(row.step(), side, "base"), nz(row.base()), BigDecimal::add);
            sums.merge(YtdAccess.key(row.step(), side, "amount"), nz(row.amount()), BigDecimal::add);
            sums.merge(
                YtdAccess.key(row.step(), side, "periods"),
                BigDecimal.valueOf(row.lines() != null ? row.lines() : 0),
                BigDecimal::add
            );
        }
        return sums;
    }

    /**
     * Everything about templates that changes a calculation's inputs, for the payroll run's
     * idempotency hash: each enabled template's id@version with its settings, and the latest change
     * to any employee profile.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public String fingerprint() {
        String adoptions = adoptionRepository
            .findByEnabledTrue()
            .stream()
            .sorted(Comparator.comparing(PayrollTemplateAdoption::getTemplateId))
            .map(
                a ->
                    catalog.find(a.getTemplateId()).map(PayrollTemplate::ref).orElse(a.getTemplateId() + "@unavailable") +
                    new TreeMap<>(a.getSettings())
            )
            .collect(Collectors.joining(";"));
        Instant profiles = profileRepository.findMaxLastModifiedDate().orElse(null);
        return adoptions + "|" + (profiles == null ? "0" : profiles.toString());
    }

    static LineSide sideOf(PayrollLineCategory category) {
        if (category == null) return null;
        return switch (category) {
            case WITHHOLDING -> LineSide.EMPLOYEE;
            case EMPLOYER_CHARGE -> LineSide.EMPLOYER;
            case INCOME_TAX -> LineSide.TAX;
            default -> null;
        };
    }

    static Set<TaxType> ledgersOf(PayrollTemplate template) {
        return template.steps().stream().map(Step::ledger).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }

    // ---------------------------------------------------------------- helpers

    /** What in the tenant's existing setup interacts with a newly enabled template. */
    private List<String> warnings(PayrollTemplate template) {
        String country = template.jurisdiction().country();
        CountryCode countryCode = CountryCode.valueOf(country);
        Set<TaxType> ledgers = ledgersOf(template);
        LocalDate today = LocalDate.now();
        List<String> warnings = new ArrayList<>();

        long brackets = taxBracketRepository.count(
            (Specification<TaxBracket>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("country").get("code"), countryCode),
                    cb.or(cb.isNull(root.get("validTo")), cb.greaterThanOrEqualTo(root.get("validTo"), today))
                )
        );
        if (brackets > 0) {
            warnings.add(
                brackets +
                " tax bracket row(s) for " +
                country +
                " are ignored for " +
                country +
                " employees while this template is enabled."
            );
        }
        long withholdings = taxWithholdingRepository.count(
            (Specification<TaxWithholding>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("employee").get("country").get("code"), countryCode),
                    root.get("type").in(ledgers),
                    cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), today))
                )
        );
        if (withholdings > 0) {
            warnings.add(
                withholdings +
                " employee tax withholding(s) of types " +
                ledgers +
                " are ignored for " +
                country +
                " employees; the template computes them."
            );
        }
        for (PayComponent component : payComponentRepository.findAll()) {
            boolean hasRules = component.getPayRules().stream().anyMatch(r -> Boolean.TRUE.equals(r.getActive()));
            if (component.getKind() == Kind.EARNING && !Boolean.TRUE.equals(component.getContributesToSocial())) {
                warnings.add(
                    "Earning component " +
                    component.getCode() +
                    " (" +
                    component.getName() +
                    ") is not marked as subject to social contributions, so it is excluded from the template's contribution base. Change it under Payroll → Pay rules."
                );
            }
            if (component.getKind() == Kind.EARNING && !Boolean.TRUE.equals(component.getTaxable())) {
                warnings.add(
                    "Earning component " +
                    component.getCode() +
                    " (" +
                    component.getName() +
                    ") is not taxable, so it is excluded from the template's tax base. Change it under Payroll → Pay rules."
                );
            }
            if (hasRules && (component.getCode() == PayComponentCode.TAX_PIT || component.getCode() == PayComponentCode.EMPLOYER_CHARGE)) {
                warnings.add(
                    "Formula rules on " + component.getCode() + " are skipped for " + country + " employees; the template replaces them."
                );
            } else if (hasRules && (component.getKind() == Kind.DEDUCTION || component.getKind() == Kind.EMPLOYER_CHARGE)) {
                warnings.add(
                    "Component " +
                    component.getCode() +
                    " (" +
                    component.getName() +
                    ") has formula rules that still run for " +
                    country +
                    " employees; make sure they don't duplicate statutory amounts."
                );
            }
        }
        Set<String> frequencies = payrollCalendarRepository
            .findAll()
            .stream()
            .map(PayrollCalendar::getFrequency)
            .filter(f -> f != null && !template.payFrequencies().contains(f))
            .map(Enum::name)
            .collect(Collectors.toCollection(java.util.TreeSet::new));
        if (!frequencies.isEmpty()) {
            warnings.add(
                "This template supports " +
                template.payFrequencies() +
                " pay; runs on " +
                frequencies +
                " calendars will fail for " +
                country +
                " employees."
            );
        }
        // Same rule as the template's employee list, so the two counts agree.
        List<Employee> covered = coveredEmployees(template);
        Map<UUID, Map<String, String>> profiles = profileRepository
            .findByTemplateId(template.id())
            .stream()
            .collect(Collectors.toMap(p -> p.getEmployee().getId(), EmployeeTemplateProfile::getAttributes));
        long incomplete = covered
            .stream()
            .filter(e -> !missingRequired(template.inputs().employee(), profiles.getOrDefault(e.getId(), Map.of())).isEmpty())
            .count();
        if (incomplete > 0) {
            warnings.add(
                incomplete +
                " of " +
                covered.size() +
                " " +
                country +
                " employee(s) are missing required details for this template; payroll can't calculate them until they are filled in."
            );
        }
        return warnings;
    }

    private void ensureComponent(PayComponentCode code, String name, Kind kind) {
        boolean exists = payComponentRepository.findAll().stream().anyMatch(c -> c.getCode() == code);
        if (!exists) {
            PayComponent component = new PayComponent();
            component.setCode(code);
            component.setName(name);
            component.setKind(kind);
            component.setMeasure(Measurement.AMOUNT);
            component.setTaxable(false);
            component.setContributesToSocial(false);
            payComponentRepository.save(component);
            log.info("Created pay component {} for payroll templates", code);
        }
    }

    private PayrollTemplate template(String templateId) {
        return catalog
            .find(templateId)
            .orElseThrow(() -> new EntityNotFoundException("Payroll template " + templateId + " is not available"));
    }

    private Employee employee(UUID employeeId) {
        return employeeRepository.findById(employeeId).orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));
    }

    private static Map<String, String> clean(Map<String, String> raw) {
        Map<String, String> out = new TreeMap<>();
        if (raw != null) {
            raw.forEach((k, v) -> {
                if (k != null && v != null && !v.isBlank()) out.put(k.trim(), v.trim());
            });
        }
        return out;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static List<PayrollTemplateResponse.Input> inputs(List<InputField> fields) {
        if (fields == null) return List.of();
        return fields
            .stream()
            .map(f ->
                new PayrollTemplateResponse.Input(
                    f.key(),
                    f.type().name(),
                    f.required(),
                    f.values(),
                    f.pattern(),
                    f.min(),
                    f.max(),
                    f.defaultValue(),
                    f.description()
                )
            )
            .toList();
    }

    private static PayrollTemplateResponse toResponse(PayrollTemplate t, PayrollTemplateAdoption adoption) {
        List<PayrollTemplateResponse.Source> sources = t
            .sources()
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new PayrollTemplateResponse.Source(e.getKey(), e.getValue().title(), e.getValue().url(), e.getValue().retrieved()))
            .toList();
        PayrollTemplateResponse.Adoption adoptionView = Optional.ofNullable(adoption)
            .map(a ->
                new PayrollTemplateResponse.Adoption(
                    a.isEnabled(),
                    new TreeMap<>(a.getSettings()),
                    a.getAcknowledgedVersion(),
                    !t.version().equals(a.getAcknowledgedVersion())
                )
            )
            .orElse(null);
        return new PayrollTemplateResponse(
            t.id(),
            t.name(),
            t.version(),
            t.status().name(),
            t.jurisdiction().country(),
            t.jurisdiction().subdivision(),
            t.validity().from(),
            t.validity().to(),
            t.currency(),
            t.payFrequencies(),
            t.taxYearStart(),
            PayrollTemplateCatalog.adoptable(t),
            t.changelog().stream().map(c -> new PayrollTemplateResponse.ChangelogEntry(c.version(), c.date(), c.summary())).toList(),
            sources,
            inputs(t.inputs().employer()),
            inputs(t.inputs().employee()),
            ledgersOf(t).stream().sorted().toList(),
            t.limitations() != null ? t.limitations() : List.of(),
            t.testCases() != null ? t.testCases().size() : 0,
            adoptionView
        );
    }
}
