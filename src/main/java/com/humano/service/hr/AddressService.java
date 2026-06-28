package com.humano.service.hr;

import com.humano.domain.hr.Address;
import com.humano.domain.shared.Country;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateAddressRequest;
import com.humano.dto.hr.requests.UpdateAddressRequest;
import com.humano.dto.hr.responses.AddressResponse;
import com.humano.repository.hr.AddressRepository;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing Address records owned by an employee.
 */
@Service
public class AddressService {

    private static final Logger log = LoggerFactory.getLogger(AddressService.class);

    private final AddressRepository repository;
    private final EmployeeRepository employeeRepository;
    private final CountryRepository countryRepository;

    public AddressService(AddressRepository repository, EmployeeRepository employeeRepository, CountryRepository countryRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.countryRepository = countryRepository;
    }

    @Transactional
    public AddressResponse create(CreateAddressRequest request) {
        log.debug("Request to create Address: {}", request);
        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));
        Address entity = new Address();
        entity.setEmployee(employee);
        entity.setType(request.type());
        entity.setStreet(request.street());
        entity.setBuilding(request.building());
        entity.setApartment(request.apartment());
        entity.setCity(request.city());
        entity.setState(request.state());
        entity.setPostalCode(request.postalCode());
        if (request.countryId() != null) {
            entity.setCountry(
                countryRepository
                    .findById(request.countryId())
                    .orElseThrow(() -> EntityNotFoundException.create("Country", request.countryId()))
            );
        }
        entity.setPrimary(request.primary() != null ? request.primary() : false);
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public AddressResponse update(UUID id, UpdateAddressRequest request) {
        log.debug("Request to update Address: {}", id);
        return repository
            .findById(id)
            .map(entity -> {
                if (request.type() != null) {
                    entity.setType(request.type());
                }
                if (request.street() != null) {
                    entity.setStreet(request.street());
                }
                if (request.building() != null) {
                    entity.setBuilding(request.building());
                }
                if (request.apartment() != null) {
                    entity.setApartment(request.apartment());
                }
                if (request.city() != null) {
                    entity.setCity(request.city());
                }
                if (request.state() != null) {
                    entity.setState(request.state());
                }
                if (request.postalCode() != null) {
                    entity.setPostalCode(request.postalCode());
                }
                if (request.countryId() != null) {
                    entity.setCountry(
                        countryRepository
                            .findById(request.countryId())
                            .orElseThrow(() -> EntityNotFoundException.create("Country", request.countryId()))
                    );
                }
                if (request.primary() != null) {
                    entity.setPrimary(request.primary());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Address", id));
    }

    @Transactional(readOnly = true)
    public AddressResponse getById(UUID id) {
        return repository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Address", id));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getByEmployeeId(UUID employeeId) {
        return repository.findByEmployeeId(employeeId).stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create("Address", id);
        }
        repository.deleteById(id);
    }

    private AddressResponse mapToResponse(Address e) {
        return new AddressResponse(
            e.getId(),
            e.getEmployee() != null ? e.getEmployee().getId() : null,
            e.getType(),
            e.getStreet(),
            e.getBuilding(),
            e.getApartment(),
            e.getCity(),
            e.getState(),
            e.getPostalCode(),
            e.getCountry() != null ? e.getCountry().getId() : null,
            e.getPrimary(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
