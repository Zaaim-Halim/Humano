package com.humano.service.demo;

import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.enumeration.hr.AttendanceStatus;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.EventAction;
import com.humano.domain.enumeration.hr.EventType;
import com.humano.domain.enumeration.hr.ExpenseClaimStatus;
import com.humano.domain.enumeration.hr.Gender;
import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import com.humano.domain.enumeration.hr.TrainingStatus;
import com.humano.domain.enumeration.hr.WorkLocationType;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.domain.enumeration.payroll.RunStatus;
import com.humano.domain.enumeration.payroll.TaxBracketBasis;
import com.humano.domain.enumeration.payroll.TaxCode;
import com.humano.domain.hr.Attendance;
import com.humano.domain.hr.AttendanceEvent;
import com.humano.domain.hr.Department;
import com.humano.domain.hr.EmployeeAsset;
import com.humano.domain.hr.EmployeeBankAccount;
import com.humano.domain.hr.EmployeeCertification;
import com.humano.domain.hr.EmployeeDocument;
import com.humano.domain.hr.EmployeeEducation;
import com.humano.domain.hr.EmployeeExperience;
import com.humano.domain.hr.EmployeeLanguage;
import com.humano.domain.hr.EmployeeLicense;
import com.humano.domain.hr.LeaveRequest;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.WorkPermit;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.payroll.PayComponent;
import com.humano.domain.payroll.PayrollPeriod;
import com.humano.domain.shared.AbstractReferenceData;
import com.humano.domain.shared.Employee;
import com.humano.domain.shared.User;
import com.humano.dto.hr.requests.CreateAddressRequest;
import com.humano.dto.hr.requests.CreateAttendanceRequest;
import com.humano.dto.hr.requests.CreateDepartmentRequest;
import com.humano.dto.hr.requests.CreateEmergencyContactRequest;
import com.humano.dto.hr.requests.CreateEmployeeRequest;
import com.humano.dto.hr.requests.CreateEmploymentContractRequest;
import com.humano.dto.hr.requests.CreateExpenseClaimRequest;
import com.humano.dto.hr.requests.CreateLeaveRequest;
import com.humano.dto.hr.requests.CreateOrganizationalUnitRequest;
import com.humano.dto.hr.requests.CreatePerformanceReviewRequest;
import com.humano.dto.hr.requests.CreatePositionRequest;
import com.humano.dto.hr.requests.CreateProjectRequest;
import com.humano.dto.hr.requests.CreateTimesheetRequest;
import com.humano.dto.hr.requests.CreateTrainingRequest;
import com.humano.dto.hr.requests.EnrollEmployeeTrainingRequest;
import com.humano.dto.hr.requests.ProcessExpenseClaimRequest;
import com.humano.dto.hr.requests.ProcessLeaveRequest;
import com.humano.dto.hr.requests.UpdatePositionRequest;
import com.humano.dto.hr.responses.EmployeePersonalDetails;
import com.humano.dto.hr.responses.ReferenceDataRef;
import com.humano.dto.payroll.request.ApprovePayrollRunRequest;
import com.humano.dto.payroll.request.CreateCompensationRequest;
import com.humano.dto.payroll.request.CreatePayComponentRequest;
import com.humano.dto.payroll.request.CreatePayRuleRequest;
import com.humano.dto.payroll.request.CreateTaxBracketRequest;
import com.humano.dto.payroll.request.GeneratePayrollPeriodsRequest;
import com.humano.dto.payroll.request.InitiatePayrollRunRequest;
import com.humano.dto.payroll.response.PayrollRunResponse;
import com.humano.repository.hr.DepartmentRepository;
import com.humano.repository.hr.EmploymentTypeRepository;
import com.humano.repository.hr.ExpenseClaimRepository;
import com.humano.repository.hr.JobGradeRepository;
import com.humano.repository.hr.JobLevelRepository;
import com.humano.repository.hr.LeaveRequestRepository;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.payroll.PayComponentRepository;
import com.humano.repository.payroll.PayrollCalendarRepository;
import com.humano.repository.payroll.PayrollPeriodRepository;
import com.humano.repository.payroll.PayrollRunRepository;
import com.humano.repository.payroll.PayslipRepository;
import com.humano.repository.payroll.TaxBracketRepository;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.AddressService;
import com.humano.service.hr.AttendanceService;
import com.humano.service.hr.DepartmentService;
import com.humano.service.hr.EmergencyContactService;
import com.humano.service.hr.EmployeeProfileService;
import com.humano.service.hr.EmploymentContractService;
import com.humano.service.hr.ExpenseClaimService;
import com.humano.service.hr.LeaveRequestService;
import com.humano.service.hr.OrganizationalUnitService;
import com.humano.service.hr.PerformanceReviewService;
import com.humano.service.hr.PositionService;
import com.humano.service.hr.ProjectService;
import com.humano.service.hr.TimesheetService;
import com.humano.service.hr.TrainingService;
import com.humano.service.payroll.CompensationService;
import com.humano.service.payroll.PayComponentService;
import com.humano.service.payroll.PayrollCalendarService;
import com.humano.service.payroll.PayrollProcessingService;
import com.humano.service.payroll.PayslipService;
import com.humano.service.payroll.TaxCalculationService;
import com.humano.service.storage.StorageFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fills a tenant with a realistic sample company, so every screen has something to show.
 * <p>
 * Used for the dev demo tenant ({@code DevDataSeeder}, disable with
 * {@code humano.dev.seed.demo-data=false}) and for the demo workspaces the platform team sets up for
 * prospects ({@link DemoWorkspaceService}); both create the persona logins first
 * ({@link DemoPersonaService}). Prototype-scoped: it keeps per-run state, so each run takes a fresh
 * instance. Everything goes through the service layer — the same
 * request records and validation the REST API uses — so the data honours the backend contract.
 * Fail-soft per section (a failing section is logged and the rest continue) and idempotent: it runs
 * once, keyed on a marker employee, and a fixed {@link Random} seed keeps the data identical across
 * fresh databases.
 *
 * <h3>What it creates</h3>
 * <ul>
 *   <li>An org-unit tree (company → departments → engineering teams), departments and positions.</li>
 *   <li>The persona logins from {@link DemoPersonaService} turned into employees with realistic names,
 *       placed in the hierarchy (CEO → department heads → managers → reports), plus ~45 more staff.</li>
 *   <li>Compensation, addresses, emergency contacts and contracts for everyone.</li>
 *   <li>Projects, two weeks of timesheets and attendance, leave and expenses in every state —
 *       new requests are routed to the approval workflow, so managers have a real inbox.</li>
 *   <li>Trainings with enrolments, performance reviews, monthly payroll periods and a calculated
 *       run for last month (best-effort: payroll needs tax configuration the demo may lack).</li>
 * </ul>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** Present once the demo company has been seeded — the idempotency marker. */
    private static final String MARKER_LOGIN = "amelia.clarke@demo-company.com";
    private static final String STAFF_DOMAIN = "demo-company.com";

    private final Random random = new Random(20261013L);
    private final LocalDate today = LocalDate.now();

    private final OrganizationalUnitService unitService;
    private final DepartmentService departmentService;
    private final PositionService positionService;
    private final EmployeeProfileService employeeProfileService;
    private final CompensationService compensationService;
    private final AddressService addressService;
    private final EmergencyContactService emergencyContactService;
    private final EmploymentContractService contractService;
    private final ProjectService projectService;
    private final TimesheetService timesheetService;
    private final AttendanceService attendanceService;
    private final LeaveRequestService leaveRequestService;
    private final ExpenseClaimService expenseClaimService;
    private final TrainingService trainingService;
    private final PerformanceReviewService reviewService;
    private final PayrollCalendarService payrollCalendarService;
    private final PayrollProcessingService payrollProcessingService;
    private final PayslipService payslipService;
    private final PayComponentRepository payComponentRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final PayComponentService payComponentService;
    private final TaxCalculationService taxCalculationService;

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthorityRepository authorityRepository;
    private final DepartmentRepository departmentRepository;
    private final OrganizationalUnitRepository unitRepository;
    private final PositionRepository positionRepository;
    private final CountryRepository countryRepository;
    private final CurrencyRepository currencyRepository;
    private final EmploymentTypeRepository employmentTypeRepository;
    private final JobLevelRepository jobLevelRepository;
    private final JobGradeRepository jobGradeRepository;
    private final PayrollCalendarRepository payrollCalendarRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ExpenseClaimRepository expenseClaimRepository;
    private final PayslipRepository payslipRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final TransactionTemplate tenantTx;
    private final StorageFactory storageFactory;

    @PersistenceContext(unitName = "tenant")
    private EntityManager entityManager;

    // Resolved during seeding.
    private final Map<String, UUID> unitIds = new HashMap<>();
    private final Map<String, UUID> departmentIds = new HashMap<>();
    private final Map<String, UUID> positionIds = new HashMap<>();
    /** Every seeded employee, in creation order (managers before their reports). */
    private final List<Seeded> people = new ArrayList<>();
    private final Map<String, Seeded> byKey = new HashMap<>();
    private UUID usaId;
    private UUID usdId;

    public DemoDataSeeder(
        OrganizationalUnitService unitService,
        DepartmentService departmentService,
        PositionService positionService,
        EmployeeProfileService employeeProfileService,
        CompensationService compensationService,
        AddressService addressService,
        EmergencyContactService emergencyContactService,
        EmploymentContractService contractService,
        ProjectService projectService,
        TimesheetService timesheetService,
        AttendanceService attendanceService,
        LeaveRequestService leaveRequestService,
        ExpenseClaimService expenseClaimService,
        TrainingService trainingService,
        PerformanceReviewService reviewService,
        PayrollCalendarService payrollCalendarService,
        PayrollProcessingService payrollProcessingService,
        PayslipService payslipService,
        PayComponentRepository payComponentRepository,
        TaxBracketRepository taxBracketRepository,
        PayComponentService payComponentService,
        TaxCalculationService taxCalculationService,
        UserRepository userRepository,
        EmployeeRepository employeeRepository,
        AuthorityRepository authorityRepository,
        DepartmentRepository departmentRepository,
        OrganizationalUnitRepository unitRepository,
        PositionRepository positionRepository,
        CountryRepository countryRepository,
        CurrencyRepository currencyRepository,
        EmploymentTypeRepository employmentTypeRepository,
        JobLevelRepository jobLevelRepository,
        JobGradeRepository jobGradeRepository,
        PayrollCalendarRepository payrollCalendarRepository,
        LeaveRequestRepository leaveRequestRepository,
        ExpenseClaimRepository expenseClaimRepository,
        PayslipRepository payslipRepository,
        PayrollPeriodRepository payrollPeriodRepository,
        PayrollRunRepository payrollRunRepository,
        @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTransactionManager,
        StorageFactory storageFactory
    ) {
        this.unitService = unitService;
        this.departmentService = departmentService;
        this.positionService = positionService;
        this.employeeProfileService = employeeProfileService;
        this.compensationService = compensationService;
        this.addressService = addressService;
        this.emergencyContactService = emergencyContactService;
        this.contractService = contractService;
        this.projectService = projectService;
        this.timesheetService = timesheetService;
        this.attendanceService = attendanceService;
        this.leaveRequestService = leaveRequestService;
        this.expenseClaimService = expenseClaimService;
        this.trainingService = trainingService;
        this.reviewService = reviewService;
        this.payrollCalendarService = payrollCalendarService;
        this.payrollProcessingService = payrollProcessingService;
        this.payslipService = payslipService;
        this.payComponentRepository = payComponentRepository;
        this.taxBracketRepository = taxBracketRepository;
        this.payComponentService = payComponentService;
        this.taxCalculationService = taxCalculationService;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.authorityRepository = authorityRepository;
        this.departmentRepository = departmentRepository;
        this.unitRepository = unitRepository;
        this.positionRepository = positionRepository;
        this.countryRepository = countryRepository;
        this.currencyRepository = currencyRepository;
        this.employmentTypeRepository = employmentTypeRepository;
        this.jobLevelRepository = jobLevelRepository;
        this.jobGradeRepository = jobGradeRepository;
        this.payrollCalendarRepository = payrollCalendarRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.expenseClaimRepository = expenseClaimRepository;
        this.payslipRepository = payslipRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.tenantTx = new TransactionTemplate(tenantTransactionManager);
        this.storageFactory = storageFactory;
    }

    /** One seeded person: the key is the persona local part or the staff login. */
    private record Seeded(String key, UUID id, String department, String title, UUID managerId, BigDecimal salary) {}

    /** A person to create: persona (existing login) or new staff member. */
    private record Person(
        String key,
        String firstName,
        String lastName,
        Gender gender,
        String department,
        String unit,
        String title,
        String level,
        String managerKey,
        int salary,
        boolean persona
    ) {}

    /** Must run with the demo tenant already set in {@code TenantContext}. */
    public void seed() {
        if (!section("reference data", this::resolveReferenceData)) {
            return;
        }
        if (userRepository.findOneByLogin(MARKER_LOGIN).isPresent()) {
            log.info("[demo-data] demo company already seeded; checking remaining sections");
            if (!section("load people", this::loadPeople)) {
                return;
            }
            section("missing personas", this::ensureMissingPersonas);
        } else {
            log.info("[demo-data] seeding demo company…");
            if (!section("org structure", this::seedStructure) || !section("people", this::seedPeople)) {
                return;
            }
            section("department heads", this::assignHeads);
            section("employee records", this::seedEmployeeRecords);
            section("time tracking", this::seedTime);
            section("trainings", this::seedTrainings);
            section("performance reviews", this::seedReviews);
        }
        // Each of these guards itself, so a section that failed on an earlier boot is retried.
        if (inTx(() -> positionRepository.findAll().stream().noneMatch(p -> p.getParentPosition() != null))) {
            section("position hierarchy", this::seedPositionHierarchy);
        }
        if (inTx(leaveRequestRepository::count) == 0) {
            section("leave", this::seedLeave);
        }
        if (inTx(expenseClaimRepository::count) == 0) {
            section("expenses", this::seedExpenses);
        }
        if (inTx(taxBracketRepository::count) == 0) {
            section("payroll configuration", this::seedPayrollConfiguration);
        }
        if (inTx(payslipRepository::count) == 0) {
            section("payroll", this::seedPayroll);
        }
        section("payroll history", this::seedPayrollHistory);
        if (
            inTx(() ->
                entityManager
                    .createQuery("select count(c) from Compensation c where c.effectiveTo is not null", Long.class)
                    .getSingleResult()
            ) ==
            0
        ) {
            section("employment history", this::seedEmploymentHistory);
        }
        if (
            inTx(() ->
                entityManager
                    .createQuery("select count(r) from PerformanceReview r where r.reviewDate < :d", Long.class)
                    .setParameter("d", today.withDayOfYear(1))
                    .getSingleResult()
            ) ==
            0
        ) {
            section("review history", this::seedReviewHistory);
        }
        if (inTx(() -> entityManager.createQuery("select count(d) from EmployeeDocument d", Long.class).getSingleResult()) == 0) {
            section("employee documents", this::seedDocuments);
        }
        if (inTx(() -> entityManager.createQuery("select count(e) from AttendanceEvent e", Long.class).getSingleResult()) == 0) {
            section("attendance events", this::seedAttendanceEvents);
        }
        if (inTx(() -> entityManager.createQuery("select count(l) from EmployeeLanguage l", Long.class).getSingleResult()) == 0) {
            section("background records", this::seedBackgroundRecords);
        }
        log.info("[demo-data] done: {} employees in the demo company", people.size());
    }

    /** Rebuilds {@link #people} from the database when the company was seeded on an earlier boot. */
    private void loadPeople() {
        inTx(() -> {
            for (Employee e : employeeRepository.findAll()) {
                Seeded seeded = new Seeded(
                    e.getLogin(),
                    e.getId(),
                    e.getDepartment() == null ? null : e.getDepartment().getName(),
                    e.getJobTitle() == null ? "" : e.getJobTitle(),
                    e.getManager() == null ? null : e.getManager().getId(),
                    null
                );
                people.add(seeded);
                byKey.put(seeded.key().replace("@" + DemoPersonaService.EMAIL_DOMAIN, ""), seeded);
            }
            return null;
        });
    }

    /** Runs one section; logs and swallows failures so later sections still run. */
    private boolean section(String name, Runnable body) {
        try {
            body.run();
            log.info("[demo-data] {} ✓", name);
            return true;
        } catch (Exception e) {
            log.warn("[demo-data] {} failed (non-fatal): {}", name, e.getMessage(), e);
            return false;
        }
    }

    private <T> T inTx(Supplier<T> body) {
        return tenantTx.execute(status -> body.get());
    }

    // ---------------------------------------------------------------- reference data

    private void resolveReferenceData() {
        usaId = countryRepository
            .findAll()
            .stream()
            .filter(c -> c.getCode() == CountryCode.US)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("country US missing"))
            .getId();
        usdId = currencyRepository
            .findByCode(CurrencyCode.USD)
            .orElseThrow(() -> new IllegalStateException("currency USD missing"))
            .getId();
    }

    private ReferenceDataRef ref(List<? extends AbstractReferenceData> all, String code) {
        return all.stream().filter(r -> code.equals(r.getCode())).findFirst().map(ReferenceDataRef::of).orElse(null);
    }

    // ---------------------------------------------------------------- structure

    private static final Map<String, String> DEPARTMENTS = new LinkedHashMap<>();

    static {
        DEPARTMENTS.put("Executive Office", "Company leadership, strategy and governance.");
        DEPARTMENTS.put("People & Culture", "Recruiting, onboarding, benefits, learning and employee relations.");
        DEPARTMENTS.put("Finance", "Accounting, payroll, billing, expenses and financial reporting.");
        DEPARTMENTS.put("Engineering", "Builds and runs the Demo Company product platform.");
        DEPARTMENTS.put("Sales", "New business, account management and partnerships.");
        DEPARTMENTS.put("Operations", "Workplace, scheduling, time and attendance.");
        DEPARTMENTS.put("IT", "Internal systems, integrations, devices and support.");
    }

    private void seedStructure() {
        UUID root = unit("Demo Company", OrganizationalUnitType.DIVISION, null);
        for (Map.Entry<String, String> d : DEPARTMENTS.entrySet()) {
            unit(d.getKey(), OrganizationalUnitType.DEPARTMENT, root);
            UUID existing = inTx(() ->
                departmentRepository
                    .findAll()
                    .stream()
                    .filter(x -> d.getKey().equals(x.getName()))
                    .findFirst()
                    .map(Department::getId)
                    .orElse(null)
            );
            departmentIds.put(
                d.getKey(),
                existing != null
                    ? existing
                    : departmentService.createDepartment(new CreateDepartmentRequest(d.getKey(), d.getValue(), null)).id()
            );
        }
        for (String team : List.of("Platform Team", "Product Team", "Mobile Team")) {
            unit(team, OrganizationalUnitType.TEAM, unitIds.get("Engineering"));
        }
        for (String team : List.of("Enterprise Sales", "Customer Success")) {
            unit(team, OrganizationalUnitType.TEAM, unitIds.get("Sales"));
        }
        inTx(() -> {
            positionRepository.findAll().forEach(pos -> positionIds.put(pos.getName(), pos.getId()));
            return null;
        });
    }

    /** Finds an org unit by name or creates it — lets an interrupted seed resume without duplicates. */
    private UUID unit(String name, OrganizationalUnitType type, UUID parentId) {
        UUID existing = inTx(() ->
            unitRepository.findAll().stream().filter(u -> name.equals(u.getName())).findFirst().map(OrganizationalUnit::getId).orElse(null)
        );
        UUID id = existing != null
            ? existing
            : unitService.createOrganizationalUnit(new CreateOrganizationalUnitRequest(name, type, parentId, null)).id();
        unitIds.put(name, id);
        return id;
    }

    /** Position names are unique tenant-wide, so one position per title, anchored to the first unit using it. */
    private UUID position(String unit, String title, String level) {
        return positionIds.computeIfAbsent(title, k ->
            positionService.createPosition(new CreatePositionRequest(title, null, level, unitIds.get(unit), null)).id()
        );
    }

    // ---------------------------------------------------------------- people

    /** Persona logins (from DemoPersonaService) with the real person behind each one. Order = hierarchy. */
    private static List<Person> personas() {
        return List.of(
            p(
                "executive",
                "Olivia",
                "Bennett",
                Gender.FEMALE,
                "Executive Office",
                "Executive Office",
                "Chief Executive Officer",
                "PRINCIPAL",
                null,
                26000
            ),
            p(
                "organization.admin",
                "Hannah",
                "Schmidt",
                Gender.FEMALE,
                "Executive Office",
                "Executive Office",
                "Chief of Staff",
                "LEAD",
                "executive",
                15500
            ),
            p(
                "compliance.officer",
                "Rafael",
                "Costa",
                Gender.MALE,
                "Executive Office",
                "Executive Office",
                "Compliance Officer",
                "SENIOR",
                "executive",
                11800
            ),
            p(
                "hr.manager",
                "Sofia",
                "Martinez",
                Gender.FEMALE,
                "People & Culture",
                "People & Culture",
                "Head of People",
                "LEAD",
                "executive",
                15000
            ),
            p(
                "finance.manager",
                "Daniel",
                "Okafor",
                Gender.MALE,
                "Finance",
                "Finance",
                "Chief Financial Officer",
                "PRINCIPAL",
                "executive",
                21000
            ),
            p(
                "department.head",
                "Marcus",
                "Chen",
                Gender.MALE,
                "Engineering",
                "Engineering",
                "VP of Engineering",
                "PRINCIPAL",
                "executive",
                22000
            ),
            p(
                "attendance.admin",
                "Grace",
                "Thompson",
                Gender.FEMALE,
                "Operations",
                "Operations",
                "Head of Operations",
                "LEAD",
                "executive",
                13000
            ),
            p("tenant.admin", "Ethan", "Walker", Gender.MALE, "IT", "IT", "Head of IT", "LEAD", "executive", 14000),
            // People & Culture
            p(
                "hr.specialist",
                "Aisha",
                "Rahman",
                Gender.FEMALE,
                "People & Culture",
                "People & Culture",
                "HR Business Partner",
                "SENIOR",
                "hr.manager",
                8200
            ),
            p(
                "recruiter",
                "Tom",
                "Fischer",
                Gender.MALE,
                "People & Culture",
                "People & Culture",
                "Talent Acquisition Partner",
                "MID",
                "hr.manager",
                7000
            ),
            p(
                "onboarding.specialist",
                "Mei",
                "Tanaka",
                Gender.FEMALE,
                "People & Culture",
                "People & Culture",
                "Onboarding Specialist",
                "MID",
                "hr.manager",
                6400
            ),
            p(
                "benefits.admin",
                "Carlos",
                "Mendes",
                Gender.MALE,
                "People & Culture",
                "People & Culture",
                "Benefits Manager",
                "SENIOR",
                "hr.manager",
                8000
            ),
            p(
                "benefits.specialist",
                "Nora",
                "Lindqvist",
                Gender.FEMALE,
                "People & Culture",
                "People & Culture",
                "Benefits Specialist",
                "MID",
                "benefits.admin",
                6000
            ),
            p(
                "leave.admin",
                "Isabel",
                "Ruiz",
                Gender.FEMALE,
                "People & Culture",
                "People & Culture",
                "Absence Manager",
                "SENIOR",
                "hr.manager",
                7400
            ),
            p(
                "leave.approver",
                "Kwame",
                "Asante",
                Gender.MALE,
                "People & Culture",
                "People & Culture",
                "HR Operations Lead",
                "LEAD",
                "hr.manager",
                8600
            ),
            p(
                "training.admin",
                "Elena",
                "Petrova",
                Gender.FEMALE,
                "People & Culture",
                "People & Culture",
                "Learning & Development Lead",
                "LEAD",
                "hr.manager",
                8800
            ),
            p(
                "training.coordinator",
                "Liam",
                "O'Brien",
                Gender.MALE,
                "People & Culture",
                "People & Culture",
                "Training Coordinator",
                "JUNIOR",
                "training.admin",
                5200
            ),
            p(
                "performance.admin",
                "Yara",
                "Haddad",
                Gender.FEMALE,
                "People & Culture",
                "People & Culture",
                "Performance Program Manager",
                "SENIOR",
                "hr.manager",
                8400
            ),
            p(
                "performance.reviewer",
                "Jonas",
                "Weber",
                Gender.MALE,
                "People & Culture",
                "People & Culture",
                "Talent Review Specialist",
                "MID",
                "performance.admin",
                6800
            ),
            // Finance
            p("payroll.admin", "Fatima", "Zahra", Gender.FEMALE, "Finance", "Finance", "Payroll Manager", "LEAD", "finance.manager", 9800),
            p("payroll.specialist", "Ben", "Carter", Gender.MALE, "Finance", "Finance", "Payroll Specialist", "MID", "payroll.admin", 6500),
            p("payroll.viewer", "Chloe", "Dubois", Gender.FEMALE, "Finance", "Finance", "Payroll Analyst", "JUNIOR", "payroll.admin", 5400),
            p(
                "finance.specialist",
                "Arjun",
                "Mehta",
                Gender.MALE,
                "Finance",
                "Finance",
                "Financial Analyst",
                "MID",
                "finance.manager",
                7200
            ),
            p("billing.admin", "Laura", "Rossi", Gender.FEMALE, "Finance", "Finance", "Billing Manager", "SENIOR", "finance.manager", 8300),
            p(
                "billing.specialist",
                "Samuel",
                "Adeyemi",
                Gender.MALE,
                "Finance",
                "Finance",
                "Billing Specialist",
                "MID",
                "billing.admin",
                5900
            ),
            p(
                "expense.admin",
                "Anna",
                "Kowalski",
                Gender.FEMALE,
                "Finance",
                "Finance",
                "Accounts Payable Lead",
                "SENIOR",
                "finance.manager",
                7800
            ),
            p(
                "expense.approver",
                "David",
                "Levi",
                Gender.MALE,
                "Finance",
                "Finance",
                "Finance Controller",
                "LEAD",
                "finance.manager",
                10200
            ),
            p("auditor", "Ingrid", "Larsen", Gender.FEMALE, "Finance", "Finance", "Internal Auditor", "SENIOR", "finance.manager", 9000),
            p(
                "report.admin",
                "Victor",
                "Nguyen",
                Gender.MALE,
                "Finance",
                "Finance",
                "Business Intelligence Lead",
                "LEAD",
                "finance.manager",
                9400
            ),
            p("report.viewer", "Emma", "Jansen", Gender.FEMALE, "Finance", "Finance", "Reporting Analyst", "JUNIOR", "report.admin", 5600),
            // Engineering
            p(
                "manager",
                "Priya",
                "Natarajan",
                Gender.FEMALE,
                "Engineering",
                "Platform Team",
                "Engineering Manager",
                "LEAD",
                "department.head",
                14500
            ),
            p("employee", "Lucas", "Moreau", Gender.MALE, "Engineering", "Platform Team", "Software Engineer", "MID", "manager", 9200),
            p(
                "timesheet.approver",
                "Omar",
                "Farouk",
                Gender.MALE,
                "Engineering",
                "Product Team",
                "Engineering Manager",
                "LEAD",
                "department.head",
                14000
            ),
            // IT
            p("integration.admin", "Sara", "Lindgren", Gender.FEMALE, "IT", "IT", "Integration Engineer", "SENIOR", "tenant.admin", 9600),
            p("it.support", "Noah", "Kim", Gender.MALE, "IT", "IT", "IT Support Specialist", "MID", "tenant.admin", 5800),
            p("admin", "Alex", "Morgan", Gender.NON_BINARY, "IT", "IT", "HR Systems Administrator", "SENIOR", "tenant.admin", 9400)
        );
    }

    private static Person p(
        String key,
        String first,
        String last,
        Gender gender,
        String dept,
        String unit,
        String title,
        String level,
        String manager,
        int salary
    ) {
        return new Person(key, first, last, gender, dept, unit, title, level, manager, salary, true);
    }

    private static Person s(
        String first,
        String last,
        Gender g,
        String dept,
        String unit,
        String title,
        String level,
        String manager,
        int salary
    ) {
        String login = (first + "." + last).toLowerCase().replaceAll("[^a-z.]", "") + "@" + STAFF_DOMAIN;
        return new Person(login, first, last, g, dept, unit, title, level, manager, salary, false);
    }

    /** Additional staff (new logins, created through one-step provisioning). */
    private static List<Person> staff() {
        String platform = "manager";
        String product = "timesheet.approver";
        String mobileLead = "kenji.watanabe@" + STAFF_DOMAIN;
        String salesHead = "amelia.clarke@" + STAFF_DOMAIN;
        String entLead = "jack.morrison@" + STAFF_DOMAIN;
        String csLead = "lena.hoffmann@" + STAFF_DOMAIN;
        String ops = "attendance.admin";
        return List.of(
            // Engineering — Mobile team lead first so reports can reference them.
            s("Kenji", "Watanabe", Gender.MALE, "Engineering", "Mobile Team", "Engineering Manager", "LEAD", "department.head", 14200),
            s("Ava", "Johansson", Gender.FEMALE, "Engineering", "Platform Team", "Staff Software Engineer", "PRINCIPAL", platform, 13800),
            s("Mateo", "Garcia", Gender.MALE, "Engineering", "Platform Team", "Senior Software Engineer", "SENIOR", platform, 11500),
            s("Zoe", "Papadopoulos", Gender.FEMALE, "Engineering", "Platform Team", "Site Reliability Engineer", "SENIOR", platform, 11200),
            s("Ibrahim", "Diallo", Gender.MALE, "Engineering", "Platform Team", "Software Engineer", "MID", platform, 9000),
            s("Hana", "Suzuki", Gender.FEMALE, "Engineering", "Platform Team", "Junior Software Engineer", "JUNIOR", platform, 6800),
            s("Ethan", "Brooks", Gender.MALE, "Engineering", "Platform Team", "Software Engineer Intern", "INTERN", platform, 3500),
            s("Maya", "Goldberg", Gender.FEMALE, "Engineering", "Product Team", "Senior Software Engineer", "SENIOR", product, 11400),
            s("Andrei", "Popescu", Gender.MALE, "Engineering", "Product Team", "Software Engineer", "MID", product, 9100),
            s("Leila", "Karimi", Gender.FEMALE, "Engineering", "Product Team", "Product Designer", "SENIOR", product, 10200),
            s("Felix", "Novak", Gender.MALE, "Engineering", "Product Team", "QA Engineer", "MID", product, 7800),
            s("Ruth", "Abebe", Gender.FEMALE, "Engineering", "Product Team", "Product Manager", "SENIOR", product, 11900),
            s("Diego", "Alvarez", Gender.MALE, "Engineering", "Mobile Team", "Senior Mobile Engineer", "SENIOR", mobileLead, 11300),
            s("Sun", "Li", Gender.FEMALE, "Engineering", "Mobile Team", "Mobile Engineer", "MID", mobileLead, 9000),
            s("Patrick", "Murphy", Gender.MALE, "Engineering", "Mobile Team", "Mobile Engineer", "MID", mobileLead, 8900),
            s("Amara", "Okonkwo", Gender.FEMALE, "Engineering", "Mobile Team", "Junior Mobile Engineer", "JUNIOR", mobileLead, 6700),
            // Sales
            s("Amelia", "Clarke", Gender.FEMALE, "Sales", "Sales", "VP of Sales", "PRINCIPAL", "executive", 19000),
            s("Jack", "Morrison", Gender.MALE, "Sales", "Enterprise Sales", "Enterprise Sales Lead", "LEAD", salesHead, 12500),
            s("Lena", "Hoffmann", Gender.FEMALE, "Sales", "Customer Success", "Customer Success Lead", "LEAD", salesHead, 11000),
            s("Ricardo", "Silva", Gender.MALE, "Sales", "Enterprise Sales", "Account Executive", "SENIOR", entLead, 9500),
            s("Julia", "Novakova", Gender.FEMALE, "Sales", "Enterprise Sales", "Account Executive", "MID", entLead, 8200),
            s("Hassan", "Ali", Gender.MALE, "Sales", "Enterprise Sales", "Sales Development Representative", "JUNIOR", entLead, 5200),
            s(
                "Charlotte",
                "Evans",
                Gender.FEMALE,
                "Sales",
                "Enterprise Sales",
                "Sales Development Representative",
                "JUNIOR",
                entLead,
                5100
            ),
            s("Tariq", "Hussain", Gender.MALE, "Sales", "Customer Success", "Customer Success Manager", "MID", csLead, 7600),
            s("Sophie", "Laurent", Gender.FEMALE, "Sales", "Customer Success", "Customer Success Manager", "MID", csLead, 7500),
            s("Erik", "Svensson", Gender.MALE, "Sales", "Customer Success", "Support Specialist", "JUNIOR", csLead, 5000),
            s("Nadia", "Benali", Gender.FEMALE, "Sales", "Sales", "Partnerships Manager", "SENIOR", salesHead, 9800),
            // Operations
            s("Paul", "Martin", Gender.MALE, "Operations", "Operations", "Office Manager", "SENIOR", ops, 6900),
            s("Rosa", "Fernandez", Gender.FEMALE, "Operations", "Operations", "Workforce Scheduler", "MID", ops, 5800),
            s("George", "Baker", Gender.MALE, "Operations", "Operations", "Facilities Coordinator", "JUNIOR", ops, 4800),
            s("Ines", "Duarte", Gender.FEMALE, "Operations", "Operations", "Operations Analyst", "MID", ops, 6200),
            // People, Finance, IT extra hands
            s(
                "Victoria",
                "Ivanova",
                Gender.FEMALE,
                "People & Culture",
                "People & Culture",
                "HR Coordinator",
                "JUNIOR",
                "hr.specialist",
                4900
            ),
            s("Kofi", "Mensah", Gender.MALE, "Finance", "Finance", "Accountant", "MID", "expense.approver", 6600),
            s("Alice", "Moreno", Gender.FEMALE, "Finance", "Finance", "Accounts Payable Clerk", "JUNIOR", "expense.admin", 4700),
            s("Max", "Keller", Gender.MALE, "IT", "IT", "Systems Administrator", "SENIOR", "tenant.admin", 8700),
            s("Lucia", "Romano", Gender.FEMALE, "IT", "IT", "Security Analyst", "SENIOR", "tenant.admin", 9300)
        );
    }

    private void seedPeople() {
        Map<String, UUID> ids = new HashMap<>();
        var levels = inTx(jobLevelRepository::findAll);
        var grades = inTx(jobGradeRepository::findAll);
        var types = inTx(employmentTypeRepository::findAll);

        // 1. Personas: their logins are plain users; graft the employee row onto each, then fill it in.
        for (Person person : personas()) {
            seedPersona(person, ids, levels, grades, types);
        }

        // 2. Staff: brand-new logins through the same one-step provisioning the HR UI uses.
        for (Person person : staff()) {
            UUID managerId = ids.get(person.managerKey());
            UUID existingId = inTx(() -> userRepository.findOneByLogin(person.key()).map(User::getId).orElse(null));
            if (existingId != null) {
                ids.put(person.key(), existingId);
                remember(person, existingId, managerId);
                continue;
            }
            CreateEmployeeRequest request = new CreateEmployeeRequest(
                person.key(),
                person.firstName(),
                person.lastName(),
                person.key(),
                null,
                "en",
                Set.of(AuthoritiesConstants.USER),
                person.title(),
                phone(),
                startDateFor(person.level()),
                null,
                EmployeeStatus.ACTIVE,
                usaId,
                departmentIds.get(person.department()),
                position(person.unit(), person.title(), person.level()),
                unitIds.get(person.unit()),
                managerId,
                new EmployeePersonalDetails(
                    employeeNumber(),
                    birthDate(),
                    person.gender(),
                    pickOne(CITIES)[0],
                    phone(),
                    workLocation(),
                    "INTERN".equals(person.level()) ? new BigDecimal("0.50") : BigDecimal.ONE,
                    null,
                    null,
                    null
                ),
                null,
                null,
                ref(types, "INTERN".equals(person.level()) ? "INTERN" : "FULL_TIME"),
                ref(grades, gradeFor(person.level())),
                ref(levels, person.level()),
                null,
                null
            );
            UUID id = employeeProfileService.provisionEmployee(request).id();
            ids.put(person.key(), id);
            remember(person, id, managerId);
        }
    }

    /**
     * Turns a persona login into an employee: grafts the employee row onto the existing user,
     * then fills in name, job, placement and reference data. Returns false if the login is absent.
     */
    private boolean seedPersona(
        Person person,
        Map<String, UUID> ids,
        List<? extends AbstractReferenceData> levels,
        List<? extends AbstractReferenceData> grades,
        List<? extends AbstractReferenceData> types
    ) {
        String login = person.key() + "@" + DemoPersonaService.EMAIL_DOMAIN;
        UUID positionId = position(person.unit(), person.title(), person.level());
        UUID unitId = unitIds.get(person.unit());
        UUID managerId = person.managerKey() == null ? null : ids.get(person.managerKey());
        // Graft and fill in one transaction, so an interrupted boot never leaves a half-made employee.
        UUID id = inTx(() -> {
            UUID grafted = graftEmployee(login, positionId, unitId);
            if (grafted == null) {
                return null;
            }
            Employee e = employeeRepository.findById(grafted).orElseThrow();
            e.setFirstName(person.firstName());
            e.setLastName(person.lastName());
            e.setJobTitle(person.title());
            e.setStatus(EmployeeStatus.ACTIVE);
            e.setStartDate(startDateFor(person.level()));
            e.setCountry(countryRepository.getReferenceById(usaId));
            e.setNationality(countryRepository.getReferenceById(usaId));
            e.setDepartment(departmentRepository.getReferenceById(departmentIds.get(person.department())));
            e.setPosition(positionRepository.getReferenceById(positionId));
            e.setUnit(unitRepository.getReferenceById(unitId));
            e.setManager(managerId == null ? null : employeeRepository.getReferenceById(managerId));
            e.setPhone(phone());
            e.setWorkPhone(phone());
            e.setGender(person.gender());
            e.setBirthDate(birthDate());
            e.setWorkLocation(workLocation());
            e.setFte(BigDecimal.ONE);
            e.setEmployeeNumber(employeeNumber());
            e.setEmploymentType(pickAny(types, "FULL_TIME"));
            e.setLevel(pickAny(levels, person.level()));
            e.setGrade(pickAny(grades, gradeFor(person.level())));
            authorityRepository.findById(AuthoritiesConstants.EMPLOYEE).ifPresent(a -> e.getAuthorities().add(a));
            employeeRepository.saveAndFlush(e); // @PreUpdate recomputes the materialized path
            return grafted;
        });
        if (id == null) {
            log.warn("[demo-data] persona login {} not found; skipped", login);
            return false;
        }
        ids.put(person.key(), id);
        remember(person, id, managerId);
        return true;
    }

    /**
     * Personas added after the company was first seeded (e.g. the admin login) get their employee
     * record, placement and pay on the next boot.
     */
    private void ensureMissingPersonas() {
        inTx(() -> {
            unitRepository.findAll().forEach(u -> unitIds.put(u.getName(), u.getId()));
            departmentRepository.findAll().forEach(d -> departmentIds.put(d.getName(), d.getId()));
            positionRepository.findAll().forEach(p -> positionIds.put(p.getName(), p.getId()));
            // Continue employee numbering after the highest EMP-#### already issued.
            employeeCounter = employeeRepository
                .findAll()
                .stream()
                .map(Employee::getEmployeeNumber)
                .filter(n -> n != null && n.matches("EMP-\\d+"))
                .mapToInt(n -> Integer.parseInt(n.substring(4)))
                .max()
                .orElse(employeeCounter);
            return null;
        });
        Map<String, UUID> ids = new HashMap<>();
        byKey.forEach((key, seeded) -> ids.put(key, seeded.id()));
        var levels = inTx(jobLevelRepository::findAll);
        var grades = inTx(jobGradeRepository::findAll);
        var types = inTx(employmentTypeRepository::findAll);
        for (Person person : personas()) {
            boolean missing = inTx(() ->
                userRepository
                    .findOneByLogin(person.key() + "@" + DemoPersonaService.EMAIL_DOMAIN)
                    .map(u -> employeeRepository.findById(u.getId()).map(e -> e.getJobTitle() == null).orElse(true))
                    .orElse(false)
            );
            if (missing && seedPersona(person, ids, levels, grades, types)) {
                Seeded seeded = byKey.get(person.key());
                UUID positionId = positionIds.get(person.title());
                LocalDate start = inTx(() -> employeeRepository.findById(seeded.id()).orElseThrow().getStartDate());
                compensationService.createCompensation(
                    new CreateCompensationRequest(seeded.id(), positionId, usdId, seeded.salary(), Basis.MONTHLY, start, null)
                );
                log.info("[demo-data] added persona {} as an employee", person.key());
            }
        }
    }

    private void remember(Person person, UUID id, UUID managerId) {
        Seeded seeded = new Seeded(person.key(), id, person.department(), person.title(), managerId, BigDecimal.valueOf(person.salary()));
        people.removeIf(p -> p.id().equals(id)); // a repaired persona was already loaded
        people.add(seeded);
        byKey.put(person.key(), seeded);
    }

    /**
     * Adds the {@code employee} child row to an existing persona user (JOINED inheritance) so the
     * login becomes an employee without changing its id or password. Returns null if absent.
     */
    private UUID graftEmployee(String login, UUID positionId, UUID unitId) {
        User user = userRepository.findOneByLogin(login).orElse(null);
        if (user == null) {
            return null;
        }
        UUID id = user.getId();
        if (employeeRepository.existsById(id)) {
            return id;
        }
        entityManager
            .createNativeQuery(
                "INSERT INTO employee (id, status, position_id, unit_id, path) " +
                "VALUES (UUID_TO_BIN(:id), 'ACTIVE', UUID_TO_BIN(:position), UUID_TO_BIN(:unit), :path)"
            )
            .setParameter("id", id.toString())
            .setParameter("position", positionId.toString())
            .setParameter("unit", unitId.toString())
            .setParameter("path", "/" + id)
            .executeUpdate();
        entityManager.clear(); // drop the cached plain-User instance so the next load sees an Employee
        return id;
    }

    private void assignHeads() {
        Map<String, String> heads = Map.of(
            "Executive Office",
            "executive",
            "People & Culture",
            "hr.manager",
            "Finance",
            "finance.manager",
            "Engineering",
            "department.head",
            "Sales",
            "amelia.clarke@" + STAFF_DOMAIN,
            "Operations",
            "attendance.admin",
            "IT",
            "tenant.admin"
        );
        Map<String, String> teamLeads = Map.of(
            "Platform Team",
            "manager",
            "Product Team",
            "timesheet.approver",
            "Mobile Team",
            "kenji.watanabe@" + STAFF_DOMAIN,
            "Enterprise Sales",
            "jack.morrison@" + STAFF_DOMAIN,
            "Customer Success",
            "lena.hoffmann@" + STAFF_DOMAIN,
            "Demo Company",
            "executive"
        );
        inTx(() -> {
            heads.forEach((dept, key) -> {
                Seeded head = byKey.get(key);
                if (head == null) {
                    return;
                }
                Department d = departmentRepository.findById(departmentIds.get(dept)).orElseThrow();
                d.setHead(employeeRepository.getReferenceById(head.id()));
                OrganizationalUnit u = unitRepository.findById(unitIds.get(dept)).orElseThrow();
                u.setManager(employeeRepository.getReferenceById(head.id()));
            });
            teamLeads.forEach((unit, key) -> {
                Seeded lead = byKey.get(key);
                if (lead != null) {
                    unitRepository.findById(unitIds.get(unit)).orElseThrow().setManager(employeeRepository.getReferenceById(lead.id()));
                }
            });
            return null;
        });
    }

    /**
     * Links each position to the position its holders most often report to — the reporting lines
     * already encode the job architecture, so the position chart mirrors the people chart.
     */
    private void seedPositionHierarchy() {
        Map<UUID, UUID> parentOf = inTx(() -> {
            Map<UUID, Map<UUID, Integer>> votes = new HashMap<>();
            for (Employee e : employeeRepository.findAll()) {
                if (e.getManager() == null || e.getPosition() == null || e.getManager().getPosition() == null) {
                    continue;
                }
                UUID own = e.getPosition().getId();
                UUID parent = e.getManager().getPosition().getId();
                if (!own.equals(parent)) {
                    votes.computeIfAbsent(own, k -> new HashMap<>()).merge(parent, 1, Integer::sum);
                }
            }
            Map<UUID, UUID> result = new HashMap<>();
            votes.forEach((own, counts) ->
                counts.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(best -> result.put(own, best.getKey()))
            );
            return result;
        });
        int linked = 0;
        for (Map.Entry<UUID, UUID> link : parentOf.entrySet()) {
            // Skip 2-cycles (A→B and B→A): keep the edge from the less senior side only once.
            if (link.getKey().equals(parentOf.get(link.getValue()))) {
                continue;
            }
            positionService.updatePosition(link.getKey(), new UpdatePositionRequest(null, null, null, null, link.getValue()));
            linked++;
        }
        log.info("[demo-data] linked {} positions to their parent position", linked);
    }

    // ---------------------------------------------------------------- employee records

    private static final String[][] CITIES = {
        { "Austin", "TX", "73301" },
        { "Seattle", "WA", "98101" },
        { "Denver", "CO", "80202" },
        { "Boston", "MA", "02108" },
        { "Chicago", "IL", "60601" },
        { "Portland", "OR", "97201" },
        { "Raleigh", "NC", "27601" },
        { "San Diego", "CA", "92101" },
    };
    private static final String[] STREETS = {
        "Maple Avenue",
        "Oak Street",
        "Cedar Lane",
        "Pine Road",
        "Elm Street",
        "Lakeview Drive",
        "Hillcrest Road",
    };
    private static final String[] RELATIONSHIPS = { "Spouse", "Partner", "Parent", "Sibling", "Friend" };
    private static final String[] CONTACT_FIRST = {
        "Michael",
        "Sarah",
        "James",
        "Linda",
        "Robert",
        "Maria",
        "John",
        "Patricia",
        "Ahmed",
        "Mina",
    };

    private void seedEmployeeRecords() {
        for (Seeded e : people) {
            Employee entity = inTx(() -> employeeRepository.findById(e.id()).orElseThrow());
            LocalDate start = entity.getStartDate();
            UUID positionId = inTx(() -> employeeRepository.findById(e.id()).orElseThrow().getPosition().getId());
            String lastName = entity.getLastName();

            compensationService.createCompensation(
                new CreateCompensationRequest(e.id(), positionId, usdId, e.salary(), Basis.MONTHLY, start, null)
            );
            String[] city = pickOne(CITIES);
            addressService.create(
                new CreateAddressRequest(
                    e.id(),
                    "Home",
                    (100 + random.nextInt(8900)) + " " + pickOne(STREETS),
                    null,
                    random.nextInt(3) == 0 ? "Apt " + (1 + random.nextInt(40)) : null,
                    city[0],
                    city[1],
                    city[2],
                    usaId,
                    true
                )
            );
            emergencyContactService.create(
                new CreateEmergencyContactRequest(e.id(), pickOne(CONTACT_FIRST) + " " + lastName, pickOne(RELATIONSHIPS), phone(), null)
            );
            contractService.create(
                new CreateEmploymentContractRequest(
                    e.id(),
                    "CT-" + start.getYear() + "-" + String.format("%04d", people.indexOf(e) + 1),
                    start,
                    null,
                    "Permanent",
                    positionId,
                    departmentIds.get(e.department()),
                    new BigDecimal("40.0"),
                    start.minusDays(14),
                    "Active"
                )
            );
        }
    }

    // ---------------------------------------------------------------- employment history

    /** Past review comments by rating (index 0 = rating 2 … index 3 = rating 5). */
    private static final String[][] PAST_REVIEW_COMMENTS = {
        {
            "Met some goals; prioritization under pressure and estimating work are the focus for next half.",
            "Below expectations this half; we agreed a clear plan with weekly check-ins.",
        },
        {
            "Settling in well; ramping up on the team's systems and ways of working.",
            "Met most goals; communication with stakeholders is the main area to develop.",
        },
        {
            "Solid half: delivered the agreed goals and supports teammates readily.",
            "Strong ownership of a key project; next step is broader influence across teams.",
        },
        {
            "Outstanding half — exceeded goals and mentored newer colleagues.",
            "Exceptional impact; led the most important delivery of the half end to end.",
        },
    };

    /**
     * Salary history (a raise every January since joining, sometimes a promotion) and 18 months
     * of past time off. The current salary is unchanged — it
     * just starts on the latest raise date — so payroll already calculated stays consistent.
     */
    private void seedEmploymentHistory() {
        LocalDate thisYear = today.withDayOfYear(1);
        inTx(() -> {
            for (Employee e : employeeRepository.findAll()) {
                LocalDate start = e.getStartDate();
                if (start == null) {
                    continue;
                }
                Employee ref = entityManager.getReference(Employee.class, e.getId());

                // Salary: walk back from the current amount, one raise per January after the start year.
                List<Compensation> current = entityManager
                    .createQuery("select c from Compensation c where c.employee.id = :id order by c.effectiveFrom", Compensation.class)
                    .setParameter("id", e.getId())
                    .getResultList();
                if (current.size() == 1 && start.getYear() < thisYear.getYear()) {
                    Compensation now = current.get(0);
                    List<LocalDate> raises = new ArrayList<>();
                    for (int y = start.getYear() + 1; y <= thisYear.getYear(); y++) {
                        raises.add(LocalDate.of(y, 1, 1));
                    }
                    BigDecimal amount = now.getBaseAmount();
                    LocalDate to = null;
                    now.setEffectiveFrom(raises.get(raises.size() - 1));
                    for (int i = raises.size() - 1; i >= 0; i--) {
                        LocalDate from = i == 0 ? start : raises.get(i - 1);
                        to = raises.get(i).minusDays(1);
                        double pct = random.nextInt(6) == 0 ? 0.10 + random.nextInt(6) / 100.0 : 0.03 + random.nextInt(4) / 100.0;
                        amount = amount
                            .divide(BigDecimal.valueOf(1 + pct), 0, RoundingMode.HALF_UP)
                            .divide(BigDecimal.valueOf(50), 0, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(50));
                        Compensation past = new Compensation();
                        past.setEmployee(ref);
                        past.setPosition(now.getPosition());
                        past.setCurrency(now.getCurrency());
                        past.setBasis(now.getBasis());
                        past.setBaseAmount(amount);
                        past.setEffectiveFrom(from);
                        past.setEffectiveTo(to);
                        entityManager.persist(past);
                    }
                }

                Employee reviewer = e.getManager();

                // Time off: a few past requests between 18 and 2 months ago, never before joining.
                LocalDate earliest = start.isAfter(today.minusMonths(18)) ? start : today.minusMonths(18);
                LocalDate cursor = earliest.plusWeeks(2 + random.nextInt(8));
                while (cursor.isBefore(today.minusMonths(2))) {
                    LocalDate first = cursor.with(DayOfWeek.MONDAY).plusWeeks(1);
                    LeaveType type = random.nextInt(4) == 0
                        ? LeaveType.SICK
                        : (random.nextInt(12) == 0 ? LeaveType.UNPAID : LeaveType.VACATION);
                    int days = type == LeaveType.SICK ? 1 + random.nextInt(3) : 2 + random.nextInt(4);
                    LeaveRequest leave = new LeaveRequest();
                    leave.setEmployee(ref);
                    leave.setLeaveType(type);
                    leave.setStartDate(first);
                    leave.setEndDate(first.plusDays(days - 1L));
                    leave.setDaysCount(days);
                    leave.setReason(type == LeaveType.SICK ? pickOne(SICK_REASONS) : pickOne(VACATION_REASONS));
                    boolean rejected = type != LeaveType.SICK && random.nextInt(10) == 0;
                    leave.setStatus(rejected ? LeaveStatus.REJECTED : LeaveStatus.APPROVED);
                    leave.setApprover(reviewer);
                    if (rejected) {
                        leave.setApproverComments("Overlaps with a release week — please pick other dates.");
                    }
                    entityManager.persist(leave);
                    cursor = first.plusWeeks(6 + random.nextInt(14));
                }
            }
            return null;
        });
    }

    /** Half-yearly reviews (end of June and December) from the first full half after joining. */
    private void seedReviewHistory() {
        inTx(() -> {
            for (Employee e : employeeRepository.findAll()) {
                LocalDate start = e.getStartDate();
                if (start == null) {
                    continue;
                }
                Employee ref = entityManager.getReference(Employee.class, e.getId());
                Employee reviewer = e.getManager();
                if (reviewer != null) {
                    int rating = 3 + random.nextInt(2);
                    LocalDate due = start.plusMonths(5).withDayOfMonth(1);
                    due = due.getMonthValue() <= 6 ? LocalDate.of(due.getYear(), 6, 30) : LocalDate.of(due.getYear(), 12, 31);
                    while (due.isBefore(today.minusMonths(4))) {
                        if (!due.equals(LocalDate.of(today.getYear(), 6, 30))) {
                            rating = Math.max(2, Math.min(5, rating + random.nextInt(3) - 1));
                            PerformanceReview review = new PerformanceReview();
                            review.setEmployee(ref);
                            review.setReviewer(reviewer);
                            review.setReviewDate(due);
                            review.setRating(rating);
                            review.setComments(pickOne(PAST_REVIEW_COMMENTS[rating - 2]));
                            entityManager.persist(review);
                        }
                        due = due.getMonthValue() == 6 ? LocalDate.of(due.getYear(), 12, 31) : LocalDate.of(due.getYear() + 1, 6, 30);
                    }
                }
            }
            return null;
        });
    }

    // ---------------------------------------------------------------- documents

    /**
     * A few real (small, valid) PDF documents per employee — offer letter, employment contract,
     * tax form, handbook acknowledgement — stored through the tenant's storage backend, so the
     * document library lists, previews and downloads real files.
     */
    private void seedDocuments() {
        com.humano.service.storage.FileStorageService storage = storageFactory.getStorageService();
        List<Employee> employees = inTx(employeeRepository::findAll);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.US);
        for (Employee e : employees) {
            String name = e.getFirstName() + " " + e.getLastName();
            LocalDate start = e.getStartDate() == null ? today : e.getStartDate();
            String[][] docs = {
                {
                    "Offer letter",
                    "offer-letter.pdf",
                    "Offer of employment",
                    "Dear " +
                    name +
                    ", we are pleased to offer you the position of " +
                    e.getJobTitle() +
                    " starting " +
                    start.format(fmt) +
                    ".",
                },
                {
                    "Employment contract",
                    "employment-contract.pdf",
                    "Employment contract",
                    "Between Demo Company Inc. and " + name + ", effective " + start.format(fmt) + ". Position: " + e.getJobTitle() + ".",
                },
                {
                    "Tax form",
                    "w4-" + start.getYear() + ".pdf",
                    "Form W-4 (" + start.getYear() + ")",
                    "Employee's withholding certificate for " + name + ".",
                },
                {
                    "Policy acknowledgement",
                    "handbook-acknowledgement.pdf",
                    "Employee handbook acknowledgement",
                    name + " has read and accepted the employee handbook on " + start.plusDays(3).format(fmt) + ".",
                },
            };
            for (String[] d : docs) {
                byte[] pdf = simplePdf(d[2], d[3]);
                try {
                    String key = storage.store(
                        new java.io.ByteArrayInputStream(pdf),
                        "employee-documents/" + e.getId(),
                        e.getId() + "-" + d[0].replaceAll("[^A-Za-z0-9]+", "-") + "-seed.pdf",
                        "application/pdf"
                    );
                    inTx(() -> {
                        EmployeeDocument doc = new EmployeeDocument();
                        doc.setEmployee(entityManager.getReference(Employee.class, e.getId()));
                        doc.setType(d[0]);
                        doc.setFilePath(key);
                        doc.setOriginalFileName(d[1]);
                        doc.setContentType("application/pdf");
                        doc.setSizeBytes((long) pdf.length);
                        entityManager.persist(doc);
                        return null;
                    });
                } catch (java.io.IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
            }
        }
    }

    /** A one-page PDF with a title and a line of text (valid PDF 1.4, Helvetica). */
    private static byte[] simplePdf(String title, String body) {
        String safeTitle = title.replace("(", "[").replace(")", "]");
        String safeBody = body.replace("(", "[").replace(")", "]");
        String stream =
            "BT /F1 20 Tf 72 740 Td (" +
            safeTitle +
            ") Tj ET\n" +
            "BT /F1 11 Tf 72 712 Td (Demo Company Inc.) Tj ET\n" +
            "BT /F1 11 Tf 72 680 Td (" +
            safeBody +
            ") Tj ET\n";
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
            "<< /Length " + stream.length() + " >>\nstream\n" + stream + "endstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        };
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.length];
        for (int i = 0; i < objects.length; i++) {
            offsets[i] = pdf.length();
            pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    // ---------------------------------------------------------------- attendance events

    /**
     * Clock events for seeded attendance days (check-in, a lunch break, check-out), and the
     * demo company's timezone, so the time clock and attendance views show realistic days.
     */
    private void seedAttendanceEvents() {
        inTx(() -> {
            entityManager
                .createQuery("update OrganizationSettings s set s.timezone = 'Europe/Paris' where s.timezone = 'UTC'")
                .executeUpdate();
            List<Attendance> days = entityManager
                .createQuery("select a from Attendance a where a.checkIn is not null and a.checkOut is not null", Attendance.class)
                .getResultList();
            for (Attendance a : days) {
                event(a, EventType.CHECK_IN, EventAction.CHECK_IN, a.getCheckIn());
                LocalTime lunch = LocalTime.of(12, 0).plusMinutes(random.nextInt(60));
                event(a, EventType.LUNCH_BREAK, EventAction.BREAK_START, lunch);
                event(a, EventType.LUNCH_BREAK, EventAction.BREAK_END, lunch.plusMinutes(30 + random.nextInt(31)));
                if (random.nextInt(3) == 0) {
                    LocalTime coffee = LocalTime.of(15, 30).plusMinutes(random.nextInt(30));
                    event(a, EventType.OTHER, EventAction.BREAK_START, coffee);
                    event(a, EventType.OTHER, EventAction.BREAK_END, coffee.plusMinutes(10 + random.nextInt(10)));
                }
                event(a, EventType.CHECK_OUT, EventAction.CHECK_OUT, a.getCheckOut());
            }
            return null;
        });
    }

    private void event(Attendance a, EventType type, EventAction action, LocalTime time) {
        AttendanceEvent e = new AttendanceEvent();
        e.setAttendance(a);
        e.setEventType(type);
        e.setEventAction(action);
        e.setEventTime(time);
        entityManager.persist(e);
    }

    // ---------------------------------------------------------------- background records

    /** Department → (degree, field of study, certification + issuer, prior employers). */
    private static final Map<String, String[][]> BACKGROUND = Map.of(
        "Engineering",
        new String[][] {
            { "B.S.", "Computer Science" },
            { "AWS Certified Solutions Architect", "Amazon Web Services" },
            { "Stripe", "Shopify", "Atlassian", "Twilio", "Datadog" },
        },
        "Finance",
        new String[][] {
            { "B.B.A.", "Accounting" },
            { "Certified Public Accountant (CPA)", "AICPA" },
            { "Deloitte", "KPMG", "PwC", "Intuit" },
        },
        "People & Culture",
        new String[][] {
            { "B.A.", "Psychology" },
            { "SHRM Certified Professional", "SHRM" },
            { "Workday", "Gusto", "BambooHR", "Lattice" },
        },
        "Sales",
        new String[][] {
            { "B.A.", "Business Administration" },
            { "Certified Sales Professional", "NASP" },
            { "Salesforce", "HubSpot", "Oracle", "Zendesk" },
        },
        "Operations",
        new String[][] {
            { "B.S.", "Operations Management" },
            { "Project Management Professional (PMP)", "PMI" },
            { "FedEx", "Flexport", "Uber", "Instacart" },
        },
        "IT",
        new String[][] {
            { "B.S.", "Information Systems" },
            { "CompTIA Security+", "CompTIA" },
            { "Cisco", "Dell Technologies", "Okta", "CDW" },
        },
        "Executive Office",
        new String[][] {
            { "M.B.A.", "Business Administration" },
            { "Certified Director", "National Association of Corporate Directors" },
            { "McKinsey & Company", "Google", "Microsoft", "Bain & Company" },
        }
    );

    private static final String[] UNIVERSITIES = {
        "University of Washington",
        "University of California, Berkeley",
        "University of Texas at Austin",
        "New York University",
        "University of Michigan",
        "Georgia Institute of Technology",
        "Boston University",
        "Arizona State University",
    };
    private static final String[] BANKS = { "Chase", "Bank of America", "Wells Fargo", "Citibank", "U.S. Bank" };
    private static final String[] SECOND_LANGUAGES = {
        "Spanish",
        "French",
        "German",
        "Mandarin",
        "Portuguese",
        "Japanese",
        "Hindi",
        "Arabic",
    };
    private static final String[] LEVELS = { "Basic", "Intermediate", "Professional", "Fluent" };

    /** Education, past jobs, languages, certifications, bank accounts, equipment, licences and permits. */
    private void seedBackgroundRecords() {
        inTx(() -> {
            int n = 0;
            for (Employee e : employeeRepository.findAll()) {
                n++;
                Employee ref = entityManager.getReference(Employee.class, e.getId());
                String department = e.getDepartment() == null ? "Operations" : e.getDepartment().getName();
                String[][] bg = BACKGROUND.getOrDefault(department, BACKGROUND.get("Operations"));
                LocalDate start = e.getStartDate() == null ? LocalDate.now().minusYears(1) : e.getStartDate();
                LocalDate birth = e.getBirthDate() == null ? LocalDate.of(1990, 1, 1) : e.getBirthDate();
                String fullName = e.getFirstName() + " " + e.getLastName();
                String title = e.getJobTitle() == null ? "Specialist" : e.getJobTitle();

                EmployeeEducation edu = new EmployeeEducation();
                edu.setEmployee(ref);
                edu.setInstitution(pickOne(UNIVERSITIES));
                edu.setDegree(bg[0][0]);
                edu.setFieldOfStudy(bg[0][1]);
                edu.setGraduationDate(LocalDate.of(birth.getYear() + 22, 5, 15 + random.nextInt(10)));
                entityManager.persist(edu);

                // One or two previous jobs, back to back, ending shortly before joining.
                LocalDate end = start.minusWeeks(2 + random.nextInt(6));
                int jobs = 1 + random.nextInt(2);
                for (int j = 0; j < jobs; j++) {
                    LocalDate from = end.minusMonths(18 + random.nextInt(36));
                    EmployeeExperience exp = new EmployeeExperience();
                    exp.setEmployee(ref);
                    exp.setCompany(pickOne(bg[2]));
                    exp.setPosition(j == 0 ? title : "Associate " + title.replaceFirst("^(Senior|Staff|Lead|Principal) ", ""));
                    exp.setStartDate(from);
                    exp.setEndDate(end);
                    entityManager.persist(exp);
                    end = from.minusWeeks(1 + random.nextInt(8));
                }

                EmployeeLanguage english = new EmployeeLanguage();
                english.setEmployee(ref);
                english.setLanguage("English");
                english.setReading("Native");
                english.setWriting("Native");
                english.setSpeaking("Native");
                entityManager.persist(english);
                if (random.nextInt(3) > 0) {
                    String level = pickOne(LEVELS);
                    EmployeeLanguage other = new EmployeeLanguage();
                    other.setEmployee(ref);
                    other.setLanguage(pickOne(SECOND_LANGUAGES));
                    other.setReading(level);
                    other.setWriting(level.equals("Fluent") ? "Professional" : level);
                    other.setSpeaking(level);
                    entityManager.persist(other);
                }

                if (random.nextInt(2) == 0) {
                    LocalDate issued = start.minusMonths(random.nextInt(30));
                    EmployeeCertification cert = new EmployeeCertification();
                    cert.setEmployee(ref);
                    cert.setName(bg[1][0]);
                    cert.setIssuer(bg[1][1]);
                    cert.setIssueDate(issued);
                    // Some expire soon or have already expired, so renewals show up.
                    cert.setExpiryDate(issued.plusYears(3));
                    cert.setVerified(random.nextInt(4) > 0);
                    entityManager.persist(cert);
                }

                EmployeeBankAccount bank = new EmployeeBankAccount();
                bank.setEmployee(ref);
                bank.setBankName(pickOne(BANKS));
                bank.setIban(
                    String.format("US%02d%04d%010d", random.nextInt(100), random.nextInt(10000), (long) (random.nextDouble() * 1e10))
                );
                bank.setSwift("CHASUS33");
                bank.setAccountHolder(fullName);
                bank.setCurrency(CurrencyCode.USD);
                bank.setPrimary(true);
                entityManager.persist(bank);

                LocalDate issuedOn = start.plusDays(1);
                EmployeeAsset laptop = new EmployeeAsset();
                laptop.setEmployee(ref);
                laptop.setType("Laptop");
                laptop.setIdentifier(
                    (department.equals("Engineering") ? "MacBook Pro 16\" · " : "MacBook Air 13\" · ") + "LT-" + (1000 + n)
                );
                laptop.setAssignedDate(issuedOn);
                entityManager.persist(laptop);
                EmployeeAsset badge = new EmployeeAsset();
                badge.setEmployee(ref);
                badge.setType("Access card");
                badge.setIdentifier("BADGE-" + (5000 + n));
                badge.setAssignedDate(issuedOn);
                entityManager.persist(badge);
                if (department.equals("Sales") || department.equals("Executive Office") || random.nextInt(5) == 0) {
                    EmployeeAsset phone = new EmployeeAsset();
                    phone.setEmployee(ref);
                    phone.setType("Phone");
                    phone.setIdentifier("iPhone 15 · PH-" + (300 + n));
                    phone.setAssignedDate(issuedOn);
                    entityManager.persist(phone);
                }

                if (department.equals("Sales") || department.equals("Operations")) {
                    EmployeeLicense licence = new EmployeeLicense();
                    licence.setEmployee(ref);
                    licence.setName("Driver's license (Class D)");
                    licence.setIssuer("Department of Licensing");
                    licence.setIssueDate(start.minusYears(2 + random.nextInt(4)));
                    licence.setExpiryDate(licence.getIssueDate().plusYears(6));
                    licence.setVerified(true);
                    entityManager.persist(licence);
                }

                if (n % 9 == 0) {
                    WorkPermit permit = new WorkPermit();
                    permit.setEmployee(ref);
                    permit.setVisaType(n % 2 == 0 ? "H-1B" : "L-1");
                    permit.setPermitNumber("EAC" + (2_400_000_000L + random.nextInt(99_999_999)));
                    permit.setIssueDate(start.minusMonths(2));
                    permit.setExpiryDate(start.minusMonths(2).plusYears(3));
                    permit.setSponsor("Demo Company Inc.");
                    entityManager.persist(permit);
                }
            }
            return null;
        });
    }

    // ---------------------------------------------------------------- time

    private void seedTime() {
        List<UUID> projects = new ArrayList<>();
        String[][] defs = {
            { "Payroll Engine v2", "Rebuild of the payroll calculation engine for multi-country support." },
            { "Mobile App Launch", "First public release of the employee self-service mobile app." },
            { "Customer Portal", "Self-service portal for enterprise customers." },
            { "SOC 2 Readiness", "Controls, evidence collection and audit preparation." },
        };
        for (String[] d : defs) {
            projects.add(
                projectService
                    .createProject(
                        new CreateProjectRequest(d[0], d[1], today.minusMonths(4).atTime(9, 0), today.plusMonths(5).atTime(18, 0))
                    )
                    .id()
            );
        }
        // Two working weeks of timesheets for engineering; attendance for everyone.
        List<LocalDate> days = workingDays(today.minusDays(1), 10);
        for (Seeded e : people) {
            boolean engineer = "Engineering".equals(e.department());
            for (LocalDate day : days) {
                if (engineer) {
                    timesheetService.createTimesheet(
                        new CreateTimesheetRequest(
                            e.id(),
                            day,
                            BigDecimal.valueOf(7 + random.nextInt(3)).setScale(1),
                            projects.get(random.nextInt(2))
                        )
                    );
                }
                int roll = random.nextInt(20);
                AttendanceStatus status = roll == 0
                    ? AttendanceStatus.LATE
                    : roll == 1 ? AttendanceStatus.WORK_FROM_HOME : AttendanceStatus.PRESENT;
                LocalTime in = LocalTime.of(8, 30).plusMinutes(random.nextInt(45) + (status == AttendanceStatus.LATE ? 60 : 0));
                attendanceService.createAttendance(
                    new CreateAttendanceRequest(e.id(), day, in, in.plusHours(8).plusMinutes(random.nextInt(60)), status)
                );
            }
        }
    }

    // ---------------------------------------------------------------- leave & expenses

    private static final String[] VACATION_REASONS = {
        "Family vacation planned months ago; handover notes are in the team wiki.",
        "Annual leave to attend a wedding abroad, coverage arranged with my team.",
        "Taking a long weekend to rest after the quarterly release.",
        "Visiting family for the holidays, will be reachable for emergencies.",
    };
    private static final String[] SICK_REASONS = {
        "Seasonal flu, doctor recommended rest for a few days.",
        "Recovering from a minor outpatient procedure as advised by my doctor.",
    };

    private void seedLeave() {
        List<Seeded> staff = people.stream().filter(p -> p.managerId() != null).toList();
        int i = 0;
        for (Seeded e : staff) {
            int kind = i++ % 6;
            switch (kind) {
                case 0 -> leave(e, LeaveType.VACATION, today.minusDays(40), 5, LeaveStatus.APPROVED);
                case 1 -> leave(e, LeaveType.SICK, today.minusDays(18), 2, LeaveStatus.APPROVED);
                case 2 -> leave(e, LeaveType.VACATION, nextMonday().plusWeeks(2), 5, LeaveStatus.PENDING);
                case 3 -> leave(e, LeaveType.VACATION, today.minusDays(1), 4, LeaveStatus.APPROVED); // on leave today
                case 4 -> leave(e, LeaveType.UNPAID, today.plusDays(30), 3, LeaveStatus.REJECTED);
                default -> leave(e, LeaveType.VACATION, today.plusDays(10), 2, LeaveStatus.PENDING);
            }
        }
    }

    private void leave(Seeded e, LeaveType type, LocalDate start, int days, LeaveStatus outcome) {
        String reason = type == LeaveType.SICK ? pickOne(SICK_REASONS) : pickOne(VACATION_REASONS);
        if (type == LeaveType.UNPAID) {
            reason = "Unpaid leave requested to handle a personal relocation matter.";
        }
        UUID id = leaveRequestService
            .createLeaveRequest(new CreateLeaveRequest(start, start.plusDays(days - 1L), type, reason, e.id()))
            .id();
        // Creating the request routes it to the manager's approvals inbox; decided ones are then closed.
        if (outcome != LeaveStatus.PENDING) {
            String comment = outcome == LeaveStatus.APPROVED ? "Approved — enjoy your time off." : "Declined: overlaps the year-end close.";
            leaveRequestService.processLeaveRequest(id, new ProcessLeaveRequest(outcome, comment, e.managerId()));
        }
    }

    private static final String[][] EXPENSES = {
        { "Client dinner with Northwind Traders procurement team", "184.50" },
        { "Train tickets to the Chicago customer workshop", "236.00" },
        { "Conference pass — DevOps Days", "450.00" },
        { "Ergonomic keyboard for home office", "129.99" },
        { "Hotel, two nights for the Boston onsite", "612.40" },
        { "Taxi from airport to client office", "58.20" },
        { "Team offsite lunch", "312.75" },
    };

    private void seedExpenses() {
        List<Seeded> staff = people.stream().filter(p -> p.managerId() != null).toList();
        for (int i = 0; i < staff.size(); i += 2) {
            Seeded e = staff.get(i);
            String[] x = EXPENSES[i % EXPENSES.length];
            UUID id = expenseClaimService
                .createExpenseClaim(
                    new CreateExpenseClaimRequest(e.id(), today.minusDays(3 + random.nextInt(40)), new BigDecimal(x[1]), x[0])
                )
                .id();
            // Creating the claim routes it to the manager; every third one stays pending there.
            switch ((i / 2) % 3) {
                case 0 -> {}
                case 1 -> expenseClaimService.processExpenseClaim(id, new ProcessExpenseClaimRequest(ExpenseClaimStatus.APPROVED));
                default -> expenseClaimService.processExpenseClaim(id, new ProcessExpenseClaimRequest(ExpenseClaimStatus.REJECTED));
            }
        }
    }

    // ---------------------------------------------------------------- learning & performance

    private void seedTrainings() {
        Object[][] defs = {
            {
                "Security Awareness 2026",
                "KnowBe4",
                -60,
                -59,
                "Annual mandatory security and phishing awareness.",
                "Online",
                TrainingStatus.COMPLETED,
            },
            {
                "Leading High-Performing Teams",
                "Harvard ManageMentor",
                -20,
                -18,
                "Coaching, feedback and delegation for people managers.",
                "Boston, MA",
                TrainingStatus.COMPLETED,
            },
            {
                "AWS Solutions Architect – Associate",
                "AWS Training",
                14,
                18,
                "Exam preparation bootcamp.",
                "Online",
                TrainingStatus.PLANNED,
            },
            {
                "Payroll Compliance Update",
                "American Payroll Association",
                21,
                21,
                "Federal and state payroll changes for next year.",
                "Online",
                TrainingStatus.PLANNED,
            },
        };
        for (int t = 0; t < defs.length; t++) {
            Object[] d = defs[t];
            UUID trainingId = trainingService
                .createTraining(
                    new CreateTrainingRequest(
                        (String) d[0],
                        (String) d[1],
                        today.plusDays((int) d[2]),
                        today.plusDays((int) d[3]),
                        (String) d[4],
                        (String) d[5],
                        d[6] == TrainingStatus.COMPLETED ? "Certificate of completion" : null
                    )
                )
                .id();
            for (Seeded e : people) {
                boolean enrol =
                    switch (t) {
                        case 0 -> true;
                        case 1 -> e.title().contains("Manager") || e.title().contains("Lead") || e.title().startsWith("Head");
                        case 2 -> "Engineering".equals(e.department()) || "IT".equals(e.department());
                        default -> "Finance".equals(e.department()) && e.title().contains("Payroll");
                    };
                if (enrol) {
                    trainingService.enrollEmployee(new EnrollEmployeeTrainingRequest(e.id(), trainingId, (TrainingStatus) d[6], null));
                }
            }
        }
    }

    private static final String[] REVIEW_COMMENTS = {
        "Consistently delivers high-quality work and raises the bar for the team.",
        "Strong collaborator; next step is owning larger cross-team initiatives.",
        "Met expectations this cycle. Focus area: clearer written status updates.",
        "Exceptional ownership during the release crunch; a role model for peers.",
        "Solid technical growth. Would benefit from more proactive stakeholder communication.",
    };

    private void seedReviews() {
        for (Seeded e : people) {
            if (e.managerId() == null) {
                continue;
            }
            int rating = 3 + (random.nextInt(10) < 7 ? random.nextInt(2) : (random.nextBoolean() ? 2 : -1));
            reviewService.createPerformanceReview(
                new CreatePerformanceReviewRequest(
                    e.id(),
                    e.managerId(),
                    today.withDayOfYear(1).plusMonths(6).minusDays(1),
                    pickOne(REVIEW_COMMENTS),
                    Math.max(1, Math.min(5, rating))
                )
            );
        }
    }

    // ---------------------------------------------------------------- payroll

    /**
     * US-style statutory payroll, configured through the same services as the pay-rules screen:
     * progressive federal income tax (monthly brackets), the employee FICA deduction (Social
     * Security 6.2% up to the monthly wage base + Medicare 1.45%) and the matching employer share.
     */
    private void seedPayrollConfiguration() {
        LocalDate from = today.withDayOfYear(1).minusYears(1);
        // Monthly slices of taxable income → marginal rate (approximate 2026 federal schedule / 12).
        Object[][] brackets = {
            { "0", "1000", "0.10" },
            { "1000", "4000", "0.12" },
            { "4000", "8500", "0.22" },
            { "8500", "16000", "0.24" },
            { "16000", "20500", "0.32" },
            { "20500", "51000", "0.35" },
            { "51000", "10000000", "0.37" },
        };
        for (Object[] b : brackets) {
            taxCalculationService.createTaxBracket(
                new CreateTaxBracketRequest(
                    usaId,
                    TaxCode.PIT,
                    new BigDecimal((String) b[0]),
                    new BigDecimal((String) b[1]),
                    new BigDecimal((String) b[2]),
                    BigDecimal.ZERO,
                    TaxBracketBasis.PER_PERIOD,
                    from,
                    null
                )
            );
        }
        String fica = "#pct(#min(#grossSalary, 14675), 6.2) + #pct(#grossSalary, 1.45)";
        Set<PayComponentCode> existing = inTx(() ->
            payComponentRepository.findAll().stream().map(PayComponent::getCode).collect(Collectors.toSet())
        );
        if (!existing.contains(PayComponentCode.DEDUCTION)) {
            UUID id = payComponentService
                .createComponent(
                    new CreatePayComponentRequest(
                        PayComponentCode.DEDUCTION,
                        "Social Security & Medicare",
                        Kind.DEDUCTION,
                        Measurement.AMOUNT,
                        false,
                        true,
                        false,
                        null
                    )
                )
                .id();
            payComponentService.createRule(
                new CreatePayRuleRequest(
                    id,
                    fica,
                    "Employee FICA: Social Security 6.2% up to the monthly wage base, plus Medicare 1.45%.",
                    from,
                    null,
                    1,
                    null,
                    true
                )
            );
        }
        if (!existing.contains(PayComponentCode.EMPLOYER_CHARGE)) {
            UUID id = payComponentService
                .createComponent(
                    new CreatePayComponentRequest(
                        PayComponentCode.EMPLOYER_CHARGE,
                        "Employer payroll taxes",
                        Kind.EMPLOYER_CHARGE,
                        Measurement.AMOUNT,
                        false,
                        false,
                        false,
                        null
                    )
                )
                .id();
            payComponentService.createRule(
                new CreatePayRuleRequest(
                    id,
                    fica,
                    "Employer share of Social Security and Medicare, matching the employee contribution.",
                    from,
                    null,
                    1,
                    null,
                    true
                )
            );
        }
        log.info("[demo-data] payroll configured: {} income-tax brackets, FICA deduction and employer charge", brackets.length);
    }

    /** Last month's run: calculated and approved with payslips, waiting to be posted. */
    private void seedPayroll() {
        ensurePeriods();
        PayrollPeriod period = periodCovering(today.minusMonths(1).withDayOfMonth(1));
        if (period == null) {
            log.info("[demo-data] no payroll period for last month; skipping run");
            return;
        }
        runPayroll(period, false);
    }

    /**
     * The five months before last month: posted runs with payslips, so the run history and its
     * trend have something to show. Posted periods are skipped; an interrupted run is resumed.
     */
    private void seedPayrollHistory() {
        ensurePeriods();
        LocalDate lastMonth = today.minusMonths(1).withDayOfMonth(1);
        for (int back = 5; back >= 1; back--) {
            PayrollPeriod period = periodCovering(lastMonth.minusMonths(back));
            if (period != null && !inTx(() -> payrollRunRepository.existsByPeriodIdAndStatus(period.getId(), RunStatus.POSTED))) {
                runPayroll(period, true);
            }
        }
    }

    private void ensurePeriods() {
        UUID calendarId = inTx(() -> payrollCalendarRepository.findAll().stream().findFirst().orElseThrow().getId());
        LocalDate from = today.withDayOfMonth(1).minusMonths(7);
        LocalDate to = today.withDayOfMonth(1).plusMonths(2).minusDays(1);
        payrollCalendarService.generatePeriods(new GeneratePayrollPeriodsRequest(calendarId, from, to, 5, true));
    }

    /** Read back from the repository: generatePeriods only returns periods it newly created. */
    private PayrollPeriod periodCovering(LocalDate day) {
        return inTx(() ->
            payrollPeriodRepository
                .findAll()
                .stream()
                .filter(p -> !p.getStartDate().isAfter(day) && !p.getEndDate().isBefore(day))
                .findFirst()
                .orElse(null)
        );
    }

    /** Initiate → calculate → approve → payslips (→ post) for one period, resuming wherever it stopped. */
    private void runPayroll(PayrollPeriod period, boolean post) {
        // Idempotent: re-initiating the same period returns the existing run.
        PayrollRunResponse run = payrollProcessingService.initiatePayrollRun(
            new InitiatePayrollRunRequest(
                period.getId(),
                InitiatePayrollRunRequest.PayrollScope.ALL,
                List.of(),
                false,
                "Monthly payroll",
                null
            )
        );
        UUID runId = run.id();
        RunStatus status = run.status();
        if (status == RunStatus.DRAFT) {
            status = payrollProcessingService.calculatePayroll(runId).status();
        }
        if (status == RunStatus.CALCULATED) {
            Seeded approver = byKey.get("payroll.admin");
            UUID approverId = approver != null ? approver.id() : null;
            status = payrollProcessingService
                .approvePayrollRun(new ApprovePayrollRunRequest(runId, approverId, "Reviewed against last month's variance report.", false))
                .status();
        }
        if (status == RunStatus.APPROVED) {
            int payslips = payslipService.generatePayslipsForRun(runId).size();
            if (post) {
                status = payrollProcessingService.postPayrollRun(runId).status();
            }
            log.info("[demo-data] payroll {} → {} ({} payslips)", period.getCode(), status, payslips);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Reference data by code from a list of a known subtype (cast at the call site's setter). */
    @SuppressWarnings("unchecked")
    private <T extends AbstractReferenceData> T pickAny(List<? extends AbstractReferenceData> all, String code) {
        return (T) all.stream().filter(r -> code.equals(r.getCode())).findFirst().orElse(null);
    }

    private <T> T pickOne(T[] values) {
        return values[random.nextInt(values.length)];
    }

    private static String gradeFor(String level) {
        return switch (level) {
            case "INTERN", "JUNIOR" -> "G1";
            case "MID" -> "G2";
            case "SENIOR" -> "G3";
            default -> "G4";
        };
    }

    private LocalDate startDateFor(String level) {
        int maxMonths =
            switch (level) {
                case "PRINCIPAL", "LEAD" -> 72;
                case "SENIOR" -> 48;
                case "INTERN" -> 3;
                default -> 30;
            };
        // A handful of recent joiners so the dashboard feed has something fresh.
        int months = random.nextInt(6) == 0 ? random.nextInt(2) : 2 + random.nextInt(maxMonths);
        return today.minusMonths(months).minusDays(random.nextInt(28));
    }

    private LocalDate birthDate() {
        return today.minusYears(23 + random.nextInt(30)).minusDays(random.nextInt(365));
    }

    private String phone() {
        return String.format("+1 (%03d) 555-%04d", 200 + random.nextInt(700), random.nextInt(10000));
    }

    private int employeeCounter = 1000;

    private String employeeNumber() {
        return "EMP-" + (++employeeCounter);
    }

    private WorkLocationType workLocation() {
        int r = random.nextInt(10);
        return r < 5 ? WorkLocationType.HYBRID : r < 8 ? WorkLocationType.ONSITE : WorkLocationType.REMOTE;
    }

    private LocalDate nextMonday() {
        LocalDate d = today.plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** The {@code count} most recent working days up to and including {@code end}. */
    private static List<LocalDate> workingDays(LocalDate end, int count) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate d = end;
        while (out.size() < count) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                out.add(d);
            }
            d = d.minusDays(1);
        }
        return out;
    }
}
