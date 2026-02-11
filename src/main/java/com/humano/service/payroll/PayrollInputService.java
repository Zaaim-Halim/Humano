package com.humano.service.payroll;

import com.humano.domain.hr.Employee;
import com.humano.domain.payroll.PayComponent;
import com.humano.domain.payroll.PayrollInput;
import com.humano.domain.payroll.PayrollPeriod;
import com.humano.dto.payroll.request.BulkPayrollInputRequest;
import com.humano.dto.payroll.request.CreatePayrollInputRequest;
import com.humano.dto.payroll.response.PayrollInputResponse;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.payroll.PayComponentRepository;
import com.humano.repository.payroll.PayrollInputRepository;
import com.humano.repository.payroll.PayrollPeriodRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Service for managing payroll inputs including overtime hours, bonuses,
 * deductions, and other variable payroll data.
 */
@Service
@Transactional
public class PayrollInputService {

    private static final Logger log = LoggerFactory.getLogger(PayrollInputService.class);

    private final PayrollInputRepository inputRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayComponentRepository componentRepository;

    public PayrollInputService(
        PayrollInputRepository inputRepository,
        EmployeeRepository employeeRepository,
        PayrollPeriodRepository periodRepository,
        PayComponentRepository componentRepository
    ) {
        this.inputRepository = inputRepository;
        this.employeeRepository = employeeRepository;
        this.periodRepository = periodRepository;
        this.componentRepository = componentRepository;
    }

    /**
     * Creates a new payroll input for an employee.
     */
    public PayrollInputResponse createInput(CreatePayrollInputRequest request) {
        log.debug("Creating payroll input for employee: {}, component: {}", request.employeeId(), request.componentId());

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee", request.employeeId()));

        PayrollPeriod period = periodRepository
            .findById(request.periodId())
            .orElseThrow(() -> new EntityNotFoundException("PayrollPeriod", request.periodId()));

        PayComponent component = componentRepository
            .findById(request.componentId())
            .orElseThrow(() -> new EntityNotFoundException("PayComponent", request.componentId()));

        // Check if period is still open for inputs
        if (period.isClosed()) {
            throw new BusinessRuleViolationException("Cannot add inputs to closed payroll period: " + period.getCode());
        }

        // Check for duplicate input
        Optional<PayrollInput> existing = findExistingInput(employee.getId(), period.getId(), component.getId());

        PayrollInput input;
        if (existing.isPresent() && request.replaceExisting()) {
            input = existing.get();
            log.debug("Replacing existing input: {}", input.getId());
        } else if (existing.isPresent()) {
            throw new BusinessRuleViolationException(
                "Input already exists for employee " +
                employee.getId() +
                " and component " +
                component.getCode() +
                " in period " +
                period.getCode()
            );
        } else {
            input = new PayrollInput();
            input.setEmployee(employee);
            input.setPeriod(period);
            input.setComponent(component);
        }

        input.setQuantity(request.quantity());
        input.setRate(request.rate());
        input.setAmount(request.amount());
        input.setSource(request.source());
        input.setMetaJson(request.metadata());

        input = inputRepository.save(input);
        log.info("Created payroll input {} for employee {} component {}", input.getId(), employee.getId(), component.getCode());

        return toResponse(input);
    }

    /**
     * Creates multiple payroll inputs in bulk.
     */
    public List<PayrollInputResponse> createBulkInputs(BulkPayrollInputRequest request) {
        log.info("Creating bulk payroll inputs for period: {}", request.periodId());

        PayrollPeriod period = periodRepository
            .findById(request.periodId())
            .orElseThrow(() -> new EntityNotFoundException("PayrollPeriod", request.periodId()));

        if (period.isClosed()) {
            throw new BusinessRuleViolationException("Cannot add inputs to closed payroll period: " + period.getCode());
        }

        List<PayrollInput> inputs = new ArrayList<>();

        for (BulkPayrollInputRequest.PayrollInputItem item : request.inputs()) {
            Employee employee = employeeRepository
                .findById(item.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Employee", item.employeeId()));

            PayComponent component = componentRepository
                .findById(item.componentId())
                .orElseThrow(() -> new EntityNotFoundException("PayComponent", item.componentId()));

            // Handle existing inputs
            Optional<PayrollInput> existing = findExistingInput(employee.getId(), period.getId(), component.getId());

            PayrollInput input;
            if (existing.isPresent() && request.overwriteExisting()) {
                input = existing.get();
            } else if (existing.isPresent()) {
                log.warn("Skipping duplicate input for employee {} component {}", employee.getId(), component.getCode());
                continue;
            } else {
                input = new PayrollInput();
                input.setEmployee(employee);
                input.setPeriod(period);
                input.setComponent(component);
            }

            input.setQuantity(item.quantity());
            input.setRate(item.rate());
            input.setAmount(item.amount());
            input.setSource(request.source());

            inputs.add(input);
        }

        List<PayrollInput> savedInputs = inputRepository.saveAll(inputs);
        log.info("Created {} bulk payroll inputs for period {}", savedInputs.size(), period.getCode());

        return savedInputs.stream().map(this::toResponse).toList();
    }

    /**
     * Updates an existing payroll input.
     */
    public PayrollInputResponse updateInput(UUID inputId, BigDecimal quantity, BigDecimal rate, BigDecimal amount) {
        PayrollInput input = inputRepository.findById(inputId).orElseThrow(() -> new EntityNotFoundException("PayrollInput", inputId));

        if (input.getPeriod().isClosed()) {
            throw new BusinessRuleViolationException("Cannot modify inputs for closed payroll period");
        }

        if (quantity != null) input.setQuantity(quantity);
        if (rate != null) input.setRate(rate);
        if (amount != null) input.setAmount(amount);

        input = inputRepository.save(input);
        log.info("Updated payroll input {}", inputId);

        return toResponse(input);
    }

    /**
     * Deletes a payroll input.
     */
    public void deleteInput(UUID inputId) {
        PayrollInput input = inputRepository.findById(inputId).orElseThrow(() -> new EntityNotFoundException("PayrollInput", inputId));

        if (input.getPeriod().isClosed()) {
            throw new BusinessRuleViolationException("Cannot delete inputs for closed payroll period");
        }

        inputRepository.delete(input);
        log.info("Deleted payroll input {}", inputId);
    }

    /**
     * Gets all inputs for an employee in a specific period.
     */
    @Transactional(readOnly = true)
    public List<PayrollInputResponse> getEmployeeInputs(UUID employeeId, UUID periodId) {
        return inputRepository
            .findAll(
                (Specification<PayrollInput>) (root, query, cb) ->
                    cb.and(cb.equal(root.get("employee").get("id"), employeeId), cb.equal(root.get("period").get("id"), periodId))
            )
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Gets all inputs for a payroll period.
     */
    @Transactional(readOnly = true)
    public Page<PayrollInputResponse> getPeriodInputs(UUID periodId, Pageable pageable) {
        return inputRepository
            .findAll((Specification<PayrollInput>) (root, query, cb) -> cb.equal(root.get("period").get("id"), periodId), pageable)
            .map(this::toResponse);
    }

    /**
     * Gets inputs by component type for a period.
     */
    @Transactional(readOnly = true)
    public List<PayrollInputResponse> getInputsByComponent(UUID periodId, UUID componentId) {
        return inputRepository
            .findAll(
                (Specification<PayrollInput>) (root, query, cb) ->
                    cb.and(cb.equal(root.get("period").get("id"), periodId), cb.equal(root.get("component").get("id"), componentId))
            )
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Gets summary of inputs for a period grouped by component.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPeriodInputSummary(UUID periodId) {
        List<PayrollInput> inputs = inputRepository.findAll(
            (Specification<PayrollInput>) (root, query, cb) -> cb.equal(root.get("period").get("id"), periodId)
        );

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("periodId", periodId);
        summary.put("totalInputs", inputs.size());
        summary.put("uniqueEmployees", inputs.stream().map(i -> i.getEmployee().getId()).distinct().count());

        // Group by component
        Map<String, Object> byComponent = inputs
            .stream()
            .collect(
                Collectors.groupingBy(
                    i -> i.getComponent().getCode().name(),
                    Collectors.collectingAndThen(Collectors.toList(), list -> {
                        BigDecimal totalAmount = list.stream().map(this::calculateInputAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                        return Map.of("count", list.size(), "totalAmount", totalAmount);
                    })
                )
            );
        summary.put("byComponent", byComponent);

        // Group by source
        Map<String, Long> bySource = inputs
            .stream()
            .filter(i -> i.getSource() != null)
            .collect(Collectors.groupingBy(PayrollInput::getSource, Collectors.counting()));
        summary.put("bySource", bySource);

        return summary;
    }

    /**
     * Imports inputs from an external source (e.g., attendance system).
     */
    public List<PayrollInputResponse> importFromAttendance(UUID periodId, UUID componentId, List<Map<String, Object>> attendanceData) {
        log.info("Importing {} attendance records for period {}", attendanceData.size(), periodId);

        PayrollPeriod period = periodRepository
            .findById(periodId)
            .orElseThrow(() -> new EntityNotFoundException("PayrollPeriod", periodId));

        PayComponent component = componentRepository
            .findById(componentId)
            .orElseThrow(() -> new EntityNotFoundException("PayComponent", componentId));

        List<PayrollInput> inputs = new ArrayList<>();

        for (Map<String, Object> record : attendanceData) {
            UUID employeeId = UUID.fromString(record.get("employeeId").toString());
            Employee employee = employeeRepository.findById(employeeId).orElse(null);

            if (employee == null) {
                log.warn("Employee not found: {}, skipping", employeeId);
                continue;
            }

            PayrollInput input = new PayrollInput();
            input.setEmployee(employee);
            input.setPeriod(period);
            input.setComponent(component);

            if (record.containsKey("hours")) {
                input.setQuantity(new BigDecimal(record.get("hours").toString()));
            }
            if (record.containsKey("rate")) {
                input.setRate(new BigDecimal(record.get("rate").toString()));
            }
            if (record.containsKey("amount")) {
                input.setAmount(new BigDecimal(record.get("amount").toString()));
            }

            input.setSource("ATTENDANCE_IMPORT");
            inputs.add(input);
        }

        List<PayrollInput> savedInputs = inputRepository.saveAll(inputs);
        log.info("Imported {} attendance inputs", savedInputs.size());

        return savedInputs.stream().map(this::toResponse).toList();
    }

    /**
     * Validates all inputs for a period before payroll processing.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validatePeriodInputs(UUID periodId) {
        List<PayrollInput> inputs = inputRepository.findAll(
            (Specification<PayrollInput>) (root, query, cb) -> cb.equal(root.get("period").get("id"), periodId)
        );

        List<Map<String, String>> errors = new ArrayList<>();
        List<Map<String, String>> warnings = new ArrayList<>();

        for (PayrollInput input : inputs) {
            // Check for missing values
            if (input.getQuantity() == null && input.getAmount() == null) {
                errors.add(
                    Map.of(
                        "inputId",
                        input.getId().toString(),
                        "employeeId",
                        input.getEmployee().getId().toString(),
                        "component",
                        input.getComponent().getCode().name(),
                        "message",
                        "Input has neither quantity nor amount specified"
                    )
                );
            }

            // Check for negative values
            if (input.getQuantity() != null && input.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(Map.of("inputId", input.getId().toString(), "message", "Negative quantity not allowed"));
            }

            // Check for unusually high values
            BigDecimal amount = calculateInputAmount(input);
            if (amount.compareTo(new BigDecimal("100000")) > 0) {
                warnings.add(
                    Map.of(
                        "inputId",
                        input.getId().toString(),
                        "employeeId",
                        input.getEmployee().getId().toString(),
                        "message",
                        "Unusually high amount: " + amount
                    )
                );
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodId", periodId);
        result.put("totalInputs", inputs.size());
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        result.put("warnings", warnings);

        return result;
    }

    private Optional<PayrollInput> findExistingInput(UUID employeeId, UUID periodId, UUID componentId) {
        return inputRepository
            .findAll(
                (Specification<PayrollInput>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("employee").get("id"), employeeId),
                        cb.equal(root.get("period").get("id"), periodId),
                        cb.equal(root.get("component").get("id"), componentId)
                    )
            )
            .stream()
            .findFirst();
    }

    private BigDecimal calculateInputAmount(PayrollInput input) {
        if (input.getAmount() != null) {
            return input.getAmount();
        }
        if (input.getQuantity() != null && input.getRate() != null) {
            return input.getQuantity().multiply(input.getRate()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private PayrollInputResponse toResponse(PayrollInput input) {
        Employee employee = input.getEmployee();
        PayrollPeriod period = input.getPeriod();
        PayComponent component = input.getComponent();

        BigDecimal calculatedAmount = calculateInputAmount(input);

        return new PayrollInputResponse(
            input.getId(),
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            period.getId(),
            period.getCode(),
            component.getId(),
            component.getCode().name(),
            component.getName(),
            input.getQuantity(),
            input.getRate(),
            input.getAmount(),
            calculatedAmount,
            input.getSource(),
            input.getMetaJson()
        );
    }
}
