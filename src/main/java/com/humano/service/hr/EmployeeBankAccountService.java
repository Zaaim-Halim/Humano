package com.humano.service.hr;

import com.humano.domain.hr.EmployeeBankAccount;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeBankAccountRequest;
import com.humano.dto.hr.requests.UpdateEmployeeBankAccountRequest;
import com.humano.dto.hr.responses.EmployeeBankAccountResponse;
import com.humano.repository.hr.EmployeeBankAccountRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing EmployeeBankAccount records owned by an employee.
 */
@Service
public class EmployeeBankAccountService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeBankAccountService.class);

    private final EmployeeBankAccountRepository repository;
    private final EmployeeRepository employeeRepository;

    public EmployeeBankAccountService(EmployeeBankAccountRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeBankAccountResponse create(CreateEmployeeBankAccountRequest request) {
        log.debug("Request to create EmployeeBankAccount: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        EmployeeBankAccount entity = new EmployeeBankAccount();
        entity.setEmployee(employee);
        entity.setBankName(request.bankName());
        entity.setIban(request.iban());
        entity.setSwift(request.swift());
        entity.setAccountHolder(request.accountHolder());
        entity.setCurrency(request.currency());
        entity.setPrimary(request.primary() != null ? request.primary() : false);
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public EmployeeBankAccountResponse update(UUID id, UpdateEmployeeBankAccountRequest request) {
        log.debug("Request to update EmployeeBankAccount: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.bankName() != null) {
                    entity.setBankName(request.bankName());
                }
                if (request.iban() != null) {
                    entity.setIban(request.iban());
                }
                if (request.swift() != null) {
                    entity.setSwift(request.swift());
                }
                if (request.accountHolder() != null) {
                    entity.setAccountHolder(request.accountHolder());
                }
                if (request.currency() != null) {
                    entity.setCurrency(request.currency());
                }
                if (request.primary() != null) {
                    entity.setPrimary(request.primary());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeBankAccount", id));
    }

    @Transactional(readOnly = true)
    public EmployeeBankAccountResponse getById(UUID id) {
        return repository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("EmployeeBankAccount", id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeBankAccountResponse> getByEmployeeId(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("EmployeeBankAccount", id);
        }
        repository.deleteById(id);
    }

    private EmployeeBankAccountResponse mapToResponse(EmployeeBankAccount e) {
        return new EmployeeBankAccountResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getBankName(),
            e.getIban(),
            e.getSwift(),
            e.getAccountHolder(),
            e.getCurrency(),
            e.getPrimary(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
