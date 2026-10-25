package com.humano.web.rest.payroll;

import com.humano.dto.payroll.request.CreateLeaveTypeRuleRequest;
import com.humano.dto.payroll.request.UpdateLeaveTypeRuleRequest;
import com.humano.dto.payroll.response.LeaveTypeRuleResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequireFeature;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.payroll.LeaveTypeRuleService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Leave pay rules: how payroll pays approved sick, maternity, unpaid and other leave, per country.
 * Without a rule, leave of that type is paid in full.
 */
@RequireFeature(FeatureCodes.PAYROLL)
@RestController
@RequestMapping("/api/payroll/leave-pay-rules")
@RequirePermission(PermissionsConstants.MANAGE_PAY_COMPONENTS)
public class LeaveTypeRuleResource {

    private final LeaveTypeRuleService leaveTypeRuleService;

    public LeaveTypeRuleResource(LeaveTypeRuleService leaveTypeRuleService) {
        this.leaveTypeRuleService = leaveTypeRuleService;
    }

    @GetMapping
    public ResponseEntity<List<LeaveTypeRuleResponse>> list() {
        return ResponseEntity.ok(leaveTypeRuleService.getAllRules());
    }

    @PostMapping
    public ResponseEntity<LeaveTypeRuleResponse> create(@Valid @RequestBody CreateLeaveTypeRuleRequest request) {
        LeaveTypeRuleResponse result = leaveTypeRuleService.createRule(request);
        return ResponseEntity.created(URI.create("/api/payroll/leave-pay-rules/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeaveTypeRuleResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateLeaveTypeRuleRequest request) {
        return ResponseEntity.ok(leaveTypeRuleService.updateRule(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        leaveTypeRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
