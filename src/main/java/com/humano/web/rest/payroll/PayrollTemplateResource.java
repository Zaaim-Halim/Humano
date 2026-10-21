package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.ConfigurePayrollTemplateRequest;
import com.humano.dto.payroll.request.EmployeeTemplateProfileRequest;
import com.humano.dto.payroll.request.UpdateTemplateProfilesRequest;
import com.humano.dto.payroll.response.EmployeeTemplateProfileResponse;
import com.humano.dto.payroll.response.PayrollTemplateAdoptionResponse;
import com.humano.dto.payroll.response.PayrollTemplateResponse;
import com.humano.dto.payroll.response.TemplateEmployeeResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequireFeature;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.payroll.template.PayrollTemplateService;
import com.humano.service.payroll.template.catalog.PayrollTemplateCatalog;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The statutory payroll template catalog: browse shipped templates, adopt one for the tenant, and
 * maintain employees' template profiles.
 */
@RequireFeature(FeatureCodes.PAYROLL)
@RestController
@RequestMapping("/api/payroll/templates")
@RequirePermission(PermissionsConstants.MANAGE_TAX_BRACKETS)
public class PayrollTemplateResource {

    private final PayrollTemplateService templateService;

    public PayrollTemplateResource(PayrollTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<List<PayrollTemplateResponse>> list() {
        return ResponseEntity.ok(templateService.list());
    }

    /** Templates this build withheld because they failed validation or their test cases. */
    @GetMapping("/rejected")
    public ResponseEntity<List<PayrollTemplateCatalog.Rejected>> rejected() {
        return ResponseEntity.ok(templateService.rejected());
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<PayrollTemplateResponse> get(@PathVariable String templateId) {
        return ResponseEntity.ok(templateService.get(templateId));
    }

    @PutMapping("/{templateId}/adoption")
    public ResponseEntity<PayrollTemplateAdoptionResponse> configure(
        @PathVariable String templateId,
        @Valid @RequestBody ConfigurePayrollTemplateRequest request
    ) {
        return ResponseEntity.ok(templateService.configure(templateId, request));
    }

    @PostMapping("/{templateId}/adoption/acknowledge")
    public ResponseEntity<PayrollTemplateResponse> acknowledge(@PathVariable String templateId) {
        return ResponseEntity.ok(templateService.acknowledge(templateId));
    }

    /** The employees the template covers, each with their profile and the required attributes it still lacks. */
    @GetMapping("/{templateId}/employees")
    public ResponseEntity<List<TemplateEmployeeResponse>> employees(@PathVariable String templateId) {
        return ResponseEntity.ok(templateService.employees(templateId));
    }

    /** Sets the same attributes on several employees' profiles; their other attributes are kept. */
    @PatchMapping("/{templateId}/employees")
    public ResponseEntity<List<TemplateEmployeeResponse>> updateProfiles(
        @PathVariable String templateId,
        @Valid @RequestBody UpdateTemplateProfilesRequest request
    ) {
        return ResponseEntity.ok(templateService.updateProfiles(templateId, request));
    }

    @GetMapping("/{templateId}/employees/{employeeId}")
    public ResponseEntity<EmployeeTemplateProfileResponse> getProfile(@PathVariable String templateId, @PathVariable UUID employeeId) {
        return ResponseEntity.ok(templateService.getProfile(templateId, employeeId));
    }

    @PutMapping("/{templateId}/employees/{employeeId}")
    public ResponseEntity<EmployeeTemplateProfileResponse> saveProfile(
        @PathVariable String templateId,
        @PathVariable UUID employeeId,
        @RequestBody EmployeeTemplateProfileRequest request
    ) {
        return ResponseEntity.ok(templateService.saveProfile(templateId, employeeId, request.attributes()));
    }
}
