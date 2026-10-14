package com.humano.web.rest.hr;

import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeRequest;
import com.humano.dto.hr.requests.EmployeeSearchRequest;
import com.humano.dto.hr.requests.UpdateEmployeeProfileRequest;
import com.humano.dto.hr.responses.EmployeeDirectoryStatsResponse;
import com.humano.dto.hr.responses.EmployeeProfileResponse;
import com.humano.dto.hr.responses.SimpleEmployeeProfileResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.SecurityUtils;
import com.humano.security.annotation.RequireHrStaffOrEmployee;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.EmployeePhotoService;
import com.humano.service.hr.EmployeeProfileService;
import com.humano.service.storage.FileService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Employee profiles.
 * <p>
 * This class accesses the {@link Employee} entity, and needs to fetch its collection of authorities.
 * <p>
 * For a normal use-case, it would be better to have an eager relationship between User and Authority,
 * and send everything to the client side: there would be no View Model and DTO, a lot less code, and an outer-join
 * which would be good for performance.
 * <p>
 * We use a View Model and a DTO for 3 reasons:
 * <ul>
 * <li>We want to keep a lazy association between the user and the authorities, because people will
 * quite often do relationships with the user, and we don't want them to get the authorities all
 * the time for nothing (for performance reasons). This is the #1 goal: we should not impact our users'
 * application because of this use-case.</li>
 * <li> Not having an outer join causes n+1 requests to the database. This is not a real issue as
 * we have by default a second-level cache. This means on the first HTTP call we do the n+1 requests,
 * but then all authorities come from the cache, so in fact it's much better than doing an outer join
 * (which will get lots of data from the database, for each HTTP call).</li>
 * <li> As this manages users, for security reasons, we'd rather have a DTO layer.</li>
 * </ul>
 * <p>
 * Another option would be to have a specific JPA entity graph to handle this case.
 */
@RestController
@RequestMapping("/api/hr/employees")
public class EmployeeResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeResource.class);
    private static final String ENTITY_NAME = "employee";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeProfileService employeeProfileService;
    private final EmployeePhotoService employeePhotoService;

    public EmployeeResource(EmployeeProfileService employeeProfileService, EmployeePhotoService employeePhotoService) {
        this.employeeProfileService = employeeProfileService;
        this.employeePhotoService = employeePhotoService;
    }

    /**
     * {@code POST  /employees} : Provision a new employee in a single step.
     * <p>
     * Creates the backing user account (sending an activation/creation email)
     * and the HR profile together. There is no separate user-management surface
     * and no self-registration — every employee is created by an authorized user.
     *
     * @param request the combined account + profile creation request
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new employee
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping
    @RequirePermission(PermissionsConstants.CREATE_EMPLOYEE)
    public ResponseEntity<EmployeeProfileResponse> createEmployee(@Valid @RequestBody CreateEmployeeRequest request)
        throws URISyntaxException {
        LOG.debug("REST request to provision Employee with login: {}", request.login());

        EmployeeProfileResponse result = employeeProfileService.provisionEmployee(request);

        return ResponseEntity.created(new URI("/api/hr/employees/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code GET  /employees/assignable-roles} : The role/authority names that can
     * be granted to an employee on the create/edit form.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the role names in body
     */
    @GetMapping("/assignable-roles")
    @RequirePermission(PermissionsConstants.CREATE_EMPLOYEE)
    public ResponseEntity<List<String>> getAssignableRoles() {
        return ResponseEntity.ok(employeeProfileService.getAssignableRoles());
    }

    /**
     * {@code PUT  /employees/:id/photo} : Upload a profile photo (JPEG, PNG or WebP, up to 2 MB).
     * Allowed for HR with update rights, or for the employee themselves.
     *
     * @return the new image URL
     */
    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@securityExpressions.hasPermission('UPDATE_EMPLOYEE') or @securityExpressions.isCurrentUser(#id)")
    public ResponseEntity<Map<String, String>> uploadPhoto(@PathVariable UUID id, @RequestPart("file") MultipartFile file)
        throws IOException {
        LOG.debug("REST request to upload photo for Employee {}", id);
        String imageUrl = employeePhotoService.upload(id, file, SecurityUtils.getCurrentUserId().orElse(null));
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }

    /** {@code DELETE  /employees/:id/photo} : Remove the profile photo. Same access as upload. */
    @DeleteMapping("/{id}/photo")
    @PreAuthorize("@securityExpressions.hasPermission('UPDATE_EMPLOYEE') or @securityExpressions.isCurrentUser(#id)")
    public ResponseEntity<Void> removePhoto(@PathVariable UUID id) {
        employeePhotoService.remove(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code GET  /employees/:id/photo/:fileId} : The profile photo, for any signed-in employee —
     * photos are shown across the directory, org charts and approvals.
     */
    @GetMapping("/{id}/photo/{fileId}")
    @RequireHrStaffOrEmployee
    public ResponseEntity<Resource> photo(@PathVariable UUID id, @PathVariable UUID fileId) throws IOException {
        FileService.FileDownload file = employeePhotoService.download(id, fileId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
            .body(new InputStreamResource(file.content()));
    }

    /**
     * {@code PUT  /employees/{id}} : Updates an existing employee profile.
     *
     * @param id the ID of the employee to update
     * @param request the employee profile update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated employee
     */
    @PutMapping("/{id}")
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public ResponseEntity<EmployeeProfileResponse> updateEmployeeProfile(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmployeeProfileRequest request
    ) {
        LOG.debug("REST request to update Employee profile: {}", id);

        EmployeeProfileResponse result = employeeProfileService.updateEmployeeProfile(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /employees} : Get all employees with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of employees in body
     */
    @GetMapping
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<Page<SimpleEmployeeProfileResponse>> getAllEmployees(Pageable pageable) {
        LOG.debug("REST request to get all Employees");

        Page<SimpleEmployeeProfileResponse> page = employeeProfileService.getAllEmployeeProfiles(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /employees/stats} : Directory aggregates — headcount by status, department and
     * work location, and joiners in the last 90 days.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the aggregates in body
     */
    @GetMapping("/stats")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeDirectoryStatsResponse> getDirectoryStats() {
        LOG.debug("REST request to get employee directory stats");
        return ResponseEntity.ok(employeeProfileService.getDirectoryStats());
    }

    /**
     * {@code GET  /employees/:id} : Get employee by ID.
     *
     * @param id the ID of the employee to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the employee
     */
    @GetMapping("/{id}")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<EmployeeProfileResponse> getEmployee(@PathVariable UUID id) {
        LOG.debug("REST request to get Employee: {}", id);

        EmployeeProfileResponse result = employeeProfileService.getEmployeeProfileById(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST  /employees/search} : Search employees with criteria.
     *
     * @param searchRequest the search criteria
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of employees in body
     */
    @PostMapping("/search")
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public ResponseEntity<Page<SimpleEmployeeProfileResponse>> searchEmployees(
        @RequestBody EmployeeSearchRequest searchRequest,
        Pageable pageable
    ) {
        LOG.debug("REST request to search Employees with criteria: {}", searchRequest);

        Page<SimpleEmployeeProfileResponse> page = employeeProfileService.searchEmployees(searchRequest, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code DELETE  /employees/:id} : Delete employee by ID.
     *
     * @param id the ID of the employee to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @RequirePermission(PermissionsConstants.DELETE_EMPLOYEE)
    public ResponseEntity<Void> deleteEmployee(@PathVariable UUID id) {
        LOG.debug("REST request to delete Employee: {}", id);

        employeeProfileService.deleteEmployeeProfile(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
