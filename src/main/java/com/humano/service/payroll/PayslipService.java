package com.humano.service.payroll;

import com.humano.domain.payroll.*;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.PayslipSearchRequest;
import com.humano.dto.payroll.response.PayrollResultResponse;
import com.humano.dto.payroll.response.PayslipResponse;
import com.humano.repository.payroll.*;
import com.humano.repository.payroll.specification.PayslipSpecification;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing payslips including generation, retrieval,
 * PDF creation, and payslip analytics.
 */
@Service
@Transactional
public class PayslipService {

    private static final Logger log = LoggerFactory.getLogger(PayslipService.class);
    private static final DateTimeFormatter PAYSLIP_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PayslipRepository payslipRepository;
    private final PayrollResultRepository resultRepository;
    private final PayrollRunRepository runRepository;
    private final PayrollLineRepository lineRepository;
    private final EmployeeRepository employeeRepository;

    public PayslipService(
        PayslipRepository payslipRepository,
        PayrollResultRepository resultRepository,
        PayrollRunRepository runRepository,
        PayrollLineRepository lineRepository,
        EmployeeRepository employeeRepository
    ) {
        this.payslipRepository = payslipRepository;
        this.resultRepository = resultRepository;
        this.runRepository = runRepository;
        this.lineRepository = lineRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Generates payslips for all employees in a completed payroll run.
     */
    public List<PayslipResponse> generatePayslipsForRun(UUID runId) {
        log.info("Generating payslips for payroll run: {}", runId);

        PayrollRun run = runRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("PayrollRun", runId));

        if (
            run.getStatus() != com.humano.domain.enumeration.payroll.RunStatus.APPROVED &&
            run.getStatus() != com.humano.domain.enumeration.payroll.RunStatus.POSTED
        ) {
            throw new BusinessRuleViolationException(
                "Can only generate payslips for APPROVED or POSTED payroll runs. Current status: " + run.getStatus()
            );
        }

        // Get all results for this run
        List<PayrollResult> results = resultRepository.findAll(
            (Specification<PayrollResult>) (root, query, cb) -> cb.equal(root.get("run").get("id"), runId)
        );

        if (results.isEmpty()) {
            throw new BusinessRuleViolationException("No payroll results found for run: " + runId);
        }

        PayrollPeriod period = run.getPeriod();
        String periodPrefix = period.getEndDate().format(PAYSLIP_NUMBER_FORMAT);
        AtomicLong sequence = new AtomicLong(getNextPayslipSequence(periodPrefix));

        List<Payslip> payslips = new ArrayList<>();
        for (PayrollResult result : results) {
            // Check if payslip already exists for this result
            boolean exists = payslipRepository.exists(
                (Specification<Payslip>) (root, query, cb) -> cb.equal(root.get("result").get("id"), result.getId())
            );

            if (exists) {
                log.debug("Payslip already exists for result: {}", result.getId());
                continue;
            }

            Payslip payslip = new Payslip();
            payslip.setNumber(generatePayslipNumber(periodPrefix, sequence.getAndIncrement()));
            payslip.setResult(result);
            payslips.add(payslip);
        }

        List<Payslip> savedPayslips = payslipRepository.saveAll(payslips);
        log.info("Generated {} payslips for run {}", savedPayslips.size(), runId);

        return savedPayslips.stream().map(this::toResponse).toList();
    }

    /**
     * Generates a payslip for a single payroll result.
     */
    public PayslipResponse generatePayslip(UUID resultId) {
        log.debug("Generating payslip for result: {}", resultId);

        PayrollResult result = resultRepository
            .findById(resultId)
            .orElseThrow(() -> new EntityNotFoundException("PayrollResult", resultId));

        // Check if payslip already exists
        Optional<Payslip> existing = payslipRepository
            .findAll((Specification<Payslip>) (root, query, cb) -> cb.equal(root.get("result").get("id"), resultId))
            .stream()
            .findFirst();

        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        PayrollPeriod period = result.getPayrollPeriod();
        String periodPrefix = period.getEndDate().format(PAYSLIP_NUMBER_FORMAT);
        long sequence = getNextPayslipSequence(periodPrefix);

        Payslip payslip = new Payslip();
        payslip.setNumber(generatePayslipNumber(periodPrefix, sequence));
        payslip.setResult(result);

        payslip = payslipRepository.save(payslip);
        log.info("Generated payslip {} for employee {}", payslip.getNumber(), result.getEmployee().getId());

        return toResponse(payslip);
    }

    /**
     * Gets a payslip by its ID.
     */
    @Transactional(readOnly = true)
    public PayslipResponse getPayslip(UUID payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId).orElseThrow(() -> new EntityNotFoundException("Payslip", payslipId));
        return toResponse(payslip);
    }

    /**
     * Gets a payslip by its number.
     */
    @Transactional(readOnly = true)
    public PayslipResponse getPayslipByNumber(String number) {
        Payslip payslip = payslipRepository
            .findAll((Specification<Payslip>) (root, query, cb) -> cb.equal(root.get("number"), number))
            .stream()
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Payslip with number: " + number));
        return toResponse(payslip);
    }

    /**
     * Gets all payslips for an employee.
     */
    @Transactional(readOnly = true)
    public Page<PayslipResponse> getEmployeePayslips(UUID employeeId, Pageable pageable) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));

        return payslipRepository
            .findAll(
                (Specification<Payslip>) (root, query, cb) -> {
                    if (query != null) {
                        query.orderBy(cb.desc(root.get("createdDate")));
                    }
                    return cb.equal(root.get("result").get("employee").get("id"), employeeId);
                },
                pageable
            )
            .map(this::toResponse);
    }

    /**
     * Gets payslips for a specific payroll period.
     */
    @Transactional(readOnly = true)
    public Page<PayslipResponse> getPayslipsForPeriod(UUID periodId, Pageable pageable) {
        return payslipRepository
            .findAll(
                (Specification<Payslip>) (root, query, cb) -> cb.equal(root.get("result").get("payrollPeriod").get("id"), periodId),
                pageable
            )
            .map(this::toResponse);
    }

    /**
     * Gets the latest payslip for an employee.
     */
    @Transactional(readOnly = true)
    public PayslipResponse getLatestPayslip(UUID employeeId) {
        List<Payslip> payslips = payslipRepository.findAll(
            (Specification<Payslip>) (root, query, cb) -> {
                if (query != null) {
                    query.orderBy(cb.desc(root.get("createdDate")));
                }
                return cb.equal(root.get("result").get("employee").get("id"), employeeId);
            }
        );

        if (payslips.isEmpty()) {
            throw new EntityNotFoundException("No payslips found for employee: " + employeeId);
        }

        return toResponse(payslips.get(0));
    }

    /**
     * Updates the PDF URL for a payslip after PDF generation.
     */
    public PayslipResponse updatePdfUrl(UUID payslipId, String pdfUrl) {
        Payslip payslip = payslipRepository.findById(payslipId).orElseThrow(() -> new EntityNotFoundException("Payslip", payslipId));

        payslip.setPdfUrl(pdfUrl);
        payslip = payslipRepository.save(payslip);

        log.info("Updated PDF URL for payslip {}", payslipId);
        return toResponse(payslip);
    }

    /**
     * Gets year-to-date earnings summary for an employee.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getYearToDateSummary(UUID employeeId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        List<Payslip> payslips = payslipRepository.findAll(
            (Specification<Payslip>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("result").get("employee").get("id"), employeeId),
                    cb.between(root.get("result").get("payrollPeriod").get("endDate"), yearStart, yearEnd)
                )
        );

        BigDecimal totalGross = payslips.stream().map(p -> p.getResult().getGross()).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeductions = payslips
            .stream()
            .map(p -> p.getResult().getTotalDeductions())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNet = payslips.stream().map(p -> p.getResult().getNet()).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("employeeId", employeeId);
        summary.put("year", year);
        summary.put("payslipCount", payslips.size());
        summary.put("totalGross", totalGross);
        summary.put("totalDeductions", totalDeductions);
        summary.put("totalNet", totalNet);

        // Monthly breakdown
        Map<String, BigDecimal> monthlyNet = payslips
            .stream()
            .collect(
                Collectors.groupingBy(
                    p -> p.getResult().getPayrollPeriod().getEndDate().getMonth().name(),
                    Collectors.reducing(BigDecimal.ZERO, p -> p.getResult().getNet(), BigDecimal::add)
                )
            );
        summary.put("monthlyNet", monthlyNet);

        return summary;
    }

    /**
     * Searches payslips with various criteria.
     */
    @Transactional(readOnly = true)
    public Page<PayslipResponse> searchPayslips(
        UUID employeeId,
        UUID departmentId,
        UUID periodId,
        LocalDate fromDate,
        LocalDate toDate,
        Pageable pageable
    ) {
        return payslipRepository
            .findAll(
                (Specification<Payslip>) (root, query, cb) -> {
                    List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

                    if (employeeId != null) {
                        predicates.add(cb.equal(root.get("result").get("employee").get("id"), employeeId));
                    }
                    if (departmentId != null) {
                        predicates.add(cb.equal(root.get("result").get("employee").get("department").get("id"), departmentId));
                    }
                    if (periodId != null) {
                        predicates.add(cb.equal(root.get("result").get("payrollPeriod").get("id"), periodId));
                    }
                    if (fromDate != null) {
                        predicates.add(cb.greaterThanOrEqualTo(root.get("result").get("payrollPeriod").get("endDate"), fromDate));
                    }
                    if (toDate != null) {
                        predicates.add(cb.lessThanOrEqualTo(root.get("result").get("payrollPeriod").get("endDate"), toDate));
                    }

                    if (query != null) {
                        query.orderBy(cb.desc(root.get("createdDate")));
                    }

                    return predicates.isEmpty()
                        ? cb.conjunction()
                        : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                },
                pageable
            )
            .map(this::toResponse);
    }

    /**
     * Gets payslip statistics for a department.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDepartmentPayslipStats(UUID departmentId, UUID periodId) {
        List<Payslip> payslips = payslipRepository.findAll(
            (Specification<Payslip>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("result").get("employee").get("department").get("id"), departmentId),
                    cb.equal(root.get("result").get("payrollPeriod").get("id"), periodId)
                )
        );

        BigDecimal totalGross = payslips.stream().map(p -> p.getResult().getGross()).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNet = payslips.stream().map(p -> p.getResult().getNet()).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeductions = payslips
            .stream()
            .map(p -> p.getResult().getTotalDeductions())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEmployerCost = payslips.stream().map(p -> p.getResult().getEmployerCost()).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("departmentId", departmentId);
        stats.put("periodId", periodId);
        stats.put("employeeCount", payslips.size());
        stats.put("totalGross", totalGross);
        stats.put("totalDeductions", totalDeductions);
        stats.put("totalNet", totalNet);
        stats.put("totalEmployerCost", totalEmployerCost);

        if (!payslips.isEmpty()) {
            stats.put("averageGross", totalGross.divide(BigDecimal.valueOf(payslips.size()), 2, java.math.RoundingMode.HALF_UP));
            stats.put("averageNet", totalNet.divide(BigDecimal.valueOf(payslips.size()), 2, java.math.RoundingMode.HALF_UP));
        }

        return stats;
    }

    /**
     * Search payslips using multiple criteria with pagination.
     *
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of payslip responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<PayslipResponse> searchPayslipsAdvanced(PayslipSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search Payslips with criteria: {}", searchRequest);

        Specification<Payslip> specification = PayslipSpecification.withCriteria(
            searchRequest.employeeId(),
            searchRequest.payslipNumber(),
            searchRequest.payrollRunId(),
            searchRequest.minGross(),
            searchRequest.maxGross(),
            searchRequest.minNet(),
            searchRequest.maxNet(),
            searchRequest.periodStartFrom(),
            searchRequest.periodStartTo(),
            searchRequest.periodEndFrom(),
            searchRequest.periodEndTo(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return payslipRepository.findAll(specification, pageable).map(this::toResponse);
    }

    /**
     * Search payslips for a specific employee using multiple criteria with pagination.
     *
     * @param employeeId the employee ID to filter by
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of payslip responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<PayslipResponse> searchPayslipsByEmployee(UUID employeeId, PayslipSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search Payslips for Employee: {} with criteria: {}", employeeId, searchRequest);

        // Override employeeId in search request to ensure it matches the path parameter
        Specification<Payslip> specification = PayslipSpecification.withCriteria(
            employeeId,
            searchRequest.payslipNumber(),
            searchRequest.payrollRunId(),
            searchRequest.minGross(),
            searchRequest.maxGross(),
            searchRequest.minNet(),
            searchRequest.maxNet(),
            searchRequest.periodStartFrom(),
            searchRequest.periodStartTo(),
            searchRequest.periodEndFrom(),
            searchRequest.periodEndTo(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return payslipRepository.findAll(specification, pageable).map(this::toResponse);
    }

    private String generatePayslipNumber(String periodPrefix, long sequence) {
        return String.format("PS-%s-%05d", periodPrefix, sequence);
    }

    private long getNextPayslipSequence(String periodPrefix) {
        String pattern = "PS-" + periodPrefix + "-%";
        List<Payslip> existingPayslips = payslipRepository.findAll(
            (Specification<Payslip>) (root, query, cb) -> cb.like(root.get("number"), pattern)
        );

        if (existingPayslips.isEmpty()) {
            return 1;
        }

        return (
            existingPayslips
                .stream()
                .map(p -> {
                    String num = p.getNumber();
                    String[] parts = num.split("-");
                    return Long.parseLong(parts[parts.length - 1]);
                })
                .max(Long::compare)
                .orElse(0L) +
            1
        );
    }

    private PayslipResponse toResponse(Payslip payslip) {
        PayrollResult result = payslip.getResult();
        Employee employee = result.getEmployee();
        PayrollPeriod period = result.getPayrollPeriod();
        PayrollRun run = result.getRun();

        // Get payroll lines for detailed breakdown
        List<PayrollLine> lines = lineRepository.findAll(
            (Specification<PayrollLine>) (root, query, cb) -> {
                if (query != null) {
                    query.orderBy(cb.asc(root.get("sequence")));
                }
                return cb.equal(root.get("result").get("id"), result.getId());
            }
        );

        PayrollResultResponse details = buildResultDetails(result, run, period, lines);

        return new PayslipResponse(
            payslip.getId(),
            payslip.getNumber(),
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            employee.getLogin(), // Using login as employee code
            employee.getDepartment() != null ? employee.getDepartment().getName() : null,
            employee.getPosition() != null ? employee.getPosition().getName() : null,
            result.getId(),
            period.getId(),
            period.getCode(),
            period.getStartDate(),
            period.getEndDate(),
            period.getPaymentDate(),
            result.getGross(),
            result.getTotalDeductions(),
            result.getNet(),
            result.getCurrency() != null ? result.getCurrency().getCode().getCode() : null,
            payslip.getPdfUrl(),
            details
        );
    }

    private PayrollResultResponse buildResultDetails(PayrollResult result, PayrollRun run, PayrollPeriod period, List<PayrollLine> lines) {
        List<PayrollResultResponse.PayrollLineItem> earnings = new ArrayList<>();
        List<PayrollResultResponse.PayrollLineItem> deductions = new ArrayList<>();
        List<PayrollResultResponse.PayrollLineItem> employerCharges = new ArrayList<>();

        for (PayrollLine line : lines) {
            PayrollResultResponse.PayrollLineItem item = new PayrollResultResponse.PayrollLineItem(
                line.getId(),
                line.getComponent().getCode().name(),
                line.getComponent().getName(),
                line.getQuantity(),
                line.getRate(),
                line.getAmount(),
                line.getSequence() != null ? line.getSequence() : 0,
                null // explanation
            );

            switch (line.getComponent().getKind()) {
                case EARNING -> earnings.add(item);
                case DEDUCTION -> deductions.add(item);
                case EMPLOYER_CHARGE -> employerCharges.add(item);
            }
        }

        Employee employee = result.getEmployee();
        return new PayrollResultResponse(
            result.getId(),
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            employee.getLogin(), // Using login as employee code
            run.getId(),
            period.getId(),
            period.getCode(),
            period.getStartDate(),
            period.getEndDate(),
            period.getPaymentDate(),
            result.getGross(),
            result.getTotalDeductions(),
            result.getNet(),
            result.getEmployerCost(),
            result.getCurrency() != null ? result.getCurrency().getCode().getCode() : null,
            earnings,
            deductions,
            employerCharges,
            null, // payslipNumber - will be set separately if needed
            null // payslipUrl - will be set separately if needed
        );
    }
}
