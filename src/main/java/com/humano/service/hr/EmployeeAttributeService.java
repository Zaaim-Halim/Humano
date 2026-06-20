package com.humano.service.hr;

import com.humano.domain.hr.EmployeeAttribute;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.EmployeeAttributeDto;
import com.humano.dto.hr.requests.UpdateEmployeeAttributesRequest;
import com.humano.dto.hr.responses.EmployeeAttributeResponse;
import com.humano.repository.hr.EmployeeAttributeRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages an employee's custom key/value attributes — flexible fields that
 * extend the fixed employee profile (e.g. {@code "tshirt-size": "L"}).
 * <p>
 * Reads and a full-replace write only; attributes have no identity of their own
 * beyond their key, so the editor sends the complete set and this service
 * reconciles it.
 */
@Service
public class EmployeeAttributeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeAttributeService.class);
    private static final String ENTITY_NAME = "employeeAttribute";

    private final EmployeeAttributeRepository attributeRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeAttributeService(EmployeeAttributeRepository attributeRepository, EmployeeRepository employeeRepository) {
        this.attributeRepository = attributeRepository;
        this.employeeRepository = employeeRepository;
    }

    /** All attributes for an employee. 404s when the employee does not exist. */
    @Transactional(readOnly = true)
    public List<EmployeeAttributeResponse> getAttributes(UUID employeeId) {
        log.debug("REST request to get attributes for Employee: {}", employeeId);
        if (!employeeRepository.existsById(employeeId)) {
            throw new EntityNotFoundException("Employee not found with ID: " + employeeId);
        }
        return attributeRepository
            .findByEmployeeIdOrderByKeyAsc(employeeId)
            .stream()
            .map(a -> new EmployeeAttributeResponse(a.getKey(), a.getValue()))
            .toList();
    }

    /**
     * Replace an employee's attributes with the supplied set. Keys must be unique
     * within the request.
     */
    @Transactional
    public List<EmployeeAttributeResponse> replaceAttributes(UUID employeeId, UpdateEmployeeAttributesRequest request) {
        log.debug("REST request to replace {} attribute(s) for Employee: {}", request.attributes().size(), employeeId);

        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found with ID: " + employeeId));

        List<String> keys = request.attributes().stream().map(EmployeeAttributeDto::key).map(String::trim).toList();
        if (keys.stream().distinct().count() != keys.size()) {
            throw new BadRequestAlertException("Attribute keys must be unique", ENTITY_NAME, "duplicatekey");
        }

        attributeRepository.deleteByEmployeeId(employeeId);
        attributeRepository.flush();

        List<EmployeeAttribute> saved = attributeRepository.saveAll(
            request
                .attributes()
                .stream()
                .map(dto -> new EmployeeAttribute().key(dto.key().trim()).value(dto.value()).employee(employee))
                .collect(Collectors.toList())
        );

        return saved.stream().map(a -> new EmployeeAttributeResponse(a.getKey(), a.getValue())).toList();
    }
}
