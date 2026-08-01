package com.humano.web.rest.me;

import com.humano.dto.hr.requests.CreateAddressRequest;
import com.humano.dto.hr.requests.CreateEmergencyContactRequest;
import com.humano.dto.hr.requests.UpdateAddressRequest;
import com.humano.dto.hr.requests.UpdateEmergencyContactRequest;
import com.humano.dto.hr.responses.AddressResponse;
import com.humano.dto.hr.responses.EmergencyContactResponse;
import com.humano.dto.me.requests.SelfAddressRequest;
import com.humano.dto.me.requests.SelfEmergencyContactRequest;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.AddressService;
import com.humano.service.hr.EmergencyContactService;
import com.humano.service.me.MeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service maintenance of the caller's own employee-owned records &mdash; the
 * collections an employee is trusted to keep current themselves (addresses and
 * emergency contacts). Everything else (position, contract, salary, government ids,
 * bank details) stays HR-only on {@code /api/hr/**}.
 * <p>
 * Two rules hold for every endpoint here, and they are what make this safe to expose
 * to a plain {@code ROLE_EMPLOYEE}:
 * <ol>
 *   <li><b>The owner is never an input.</b> The employee id comes from the security
 *       context; the request bodies ({@link SelfAddressRequest},
 *       {@link SelfEmergencyContactRequest}) carry no {@code employeeId} field.</li>
 *   <li><b>Existing rows are ownership-checked before write.</b> Update and delete take
 *       a record id, so each first verifies the record belongs to the caller. A record
 *       owned by someone else is reported as 404, not 403 &mdash; a self-service caller
 *       has no business learning that another employee's record id exists.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/me")
public class MeProfileResource {

    private static final Logger LOG = LoggerFactory.getLogger(MeProfileResource.class);

    private final MeService meService;
    private final AddressService addressService;
    private final EmergencyContactService emergencyContactService;

    public MeProfileResource(MeService meService, AddressService addressService, EmergencyContactService emergencyContactService) {
        this.meService = meService;
        this.addressService = addressService;
        this.emergencyContactService = emergencyContactService;
    }

    // --- Addresses -------------------------------------------------------------------

    @GetMapping("/addresses")
    @RequireAuthenticated
    public List<AddressResponse> getAddresses() {
        return addressService.getByEmployeeId(currentEmployeeId());
    }

    @PostMapping("/addresses")
    @RequireAuthenticated
    public ResponseEntity<AddressResponse> createAddress(@Valid @RequestBody SelfAddressRequest request) {
        UUID employeeId = currentEmployeeId();
        LOG.debug("REST request to create own Address for employee {}", employeeId);
        AddressResponse result = addressService.create(
            new CreateAddressRequest(
                employeeId,
                request.type(),
                request.street(),
                request.building(),
                request.apartment(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.countryId(),
                request.primary()
            )
        );
        return ResponseEntity.created(URI.create("/api/me/addresses/" + result.id())).body(result);
    }

    @PutMapping("/addresses/{id}")
    @RequireAuthenticated
    public AddressResponse updateAddress(@PathVariable UUID id, @Valid @RequestBody SelfAddressRequest request) {
        requireOwned(addressService.getById(id).employeeId(), "Address", id);
        return addressService.update(
            id,
            new UpdateAddressRequest(
                request.type(),
                request.street(),
                request.building(),
                request.apartment(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.countryId(),
                request.primary()
            )
        );
    }

    @DeleteMapping("/addresses/{id}")
    @RequireAuthenticated
    public ResponseEntity<Void> deleteAddress(@PathVariable UUID id) {
        requireOwned(addressService.getById(id).employeeId(), "Address", id);
        addressService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Emergency contacts ----------------------------------------------------------

    @GetMapping("/emergency-contacts")
    @RequireAuthenticated
    public List<EmergencyContactResponse> getEmergencyContacts() {
        return emergencyContactService.getByEmployeeId(currentEmployeeId());
    }

    @PostMapping("/emergency-contacts")
    @RequireAuthenticated
    public ResponseEntity<EmergencyContactResponse> createEmergencyContact(@Valid @RequestBody SelfEmergencyContactRequest request) {
        UUID employeeId = currentEmployeeId();
        LOG.debug("REST request to create own EmergencyContact for employee {}", employeeId);
        EmergencyContactResponse result = emergencyContactService.create(
            new CreateEmergencyContactRequest(employeeId, request.name(), request.relationship(), request.phone(), request.email())
        );
        return ResponseEntity.created(URI.create("/api/me/emergency-contacts/" + result.id())).body(result);
    }

    @PutMapping("/emergency-contacts/{id}")
    @RequireAuthenticated
    public EmergencyContactResponse updateEmergencyContact(@PathVariable UUID id, @Valid @RequestBody SelfEmergencyContactRequest request) {
        requireOwned(emergencyContactService.getById(id).employeeId(), "EmergencyContact", id);
        return emergencyContactService.update(
            id,
            new UpdateEmergencyContactRequest(request.name(), request.relationship(), request.phone(), request.email())
        );
    }

    @DeleteMapping("/emergency-contacts/{id}")
    @RequireAuthenticated
    public ResponseEntity<Void> deleteEmergencyContact(@PathVariable UUID id) {
        requireOwned(emergencyContactService.getById(id).employeeId(), "EmergencyContact", id);
        emergencyContactService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Ownership -------------------------------------------------------------------

    /**
     * The caller's own employee id. {@code Employee} extends {@code User} with JOINED
     * inheritance, so the account id is the employee id.
     * <p>
     * An account with no employee row still has an id: reads then simply return nothing,
     * and a create fails with the 404 {@code AddressService}/{@code EmergencyContactService}
     * raise for a missing employee.
     */
    private UUID currentEmployeeId() {
        return meService
            .getCurrentUser()
            .map(user -> user.getId())
            .orElseThrow(() -> new EntityNotFoundException("Current user could not be found"));
    }

    /**
     * Reject a record that belongs to another employee, reported as if it did not exist.
     *
     * @param ownerId the {@code employeeId} carried by the record being written
     */
    private void requireOwned(UUID ownerId, String entityName, UUID recordId) {
        UUID self = currentEmployeeId();
        if (!self.equals(ownerId)) {
            LOG.warn("Employee {} attempted to modify {} {} owned by {}", self, entityName, recordId, ownerId);
            throw EntityNotFoundException.create(entityName, recordId);
        }
    }
}
