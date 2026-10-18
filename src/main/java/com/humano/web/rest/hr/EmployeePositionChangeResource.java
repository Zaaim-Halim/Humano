package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.PositionChangeRequest;
import com.humano.dto.hr.responses.PositionChangeResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.SecurityExpressions;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.PositionChangeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Someone's promotions, transfers and other position changes: the history, and recording a new one.
 * Pay in it (old and new salary) is only shown to, and only changed by, those who manage
 * compensation.
 */
@RestController
@RequestMapping("/api/hr/employees/{employeeId}/position-changes")
public class EmployeePositionChangeResource {

    private final PositionChangeService positionChangeService;
    private final SecurityExpressions securityExpressions;

    public EmployeePositionChangeResource(PositionChangeService positionChangeService, SecurityExpressions securityExpressions) {
        this.positionChangeService = positionChangeService;
        this.securityExpressions = securityExpressions;
    }

    /** {@code GET} : the history, newest first. */
    @GetMapping
    @RequirePermission(PermissionsConstants.READ_EMPLOYEE)
    public List<PositionChangeResponse> history(@PathVariable UUID employeeId) {
        return positionChangeService.history(employeeId, securityExpressions.hasPermission(PermissionsConstants.MANAGE_COMPENSATION));
    }

    /** {@code POST} : a promotion, demotion, transfer or correction, from a date. */
    @PostMapping
    @RequirePermission(PermissionsConstants.UPDATE_EMPLOYEE)
    public PositionChangeResponse change(@PathVariable UUID employeeId, @Valid @RequestBody PositionChangeRequest request) {
        if (request.newSalary() != null && !securityExpressions.hasPermission(PermissionsConstants.MANAGE_COMPENSATION)) {
            throw new AccessDeniedException("Changing pay needs the compensation permission");
        }
        return positionChangeService.change(employeeId, request);
    }
}
