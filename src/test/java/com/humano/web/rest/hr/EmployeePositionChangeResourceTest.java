package com.humano.web.rest.hr;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.hr.PositionChangeType;
import com.humano.dto.hr.requests.PositionChangeRequest;
import com.humano.security.PermissionsConstants;
import com.humano.security.SecurityExpressions;
import com.humano.service.hr.PositionChangeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Pay in a position change is only changed, and only shown, with the compensation permission. */
class EmployeePositionChangeResourceTest {

    private final PositionChangeService service = mock(PositionChangeService.class);
    private final SecurityExpressions security = mock(SecurityExpressions.class);
    private final EmployeePositionChangeResource resource = new EmployeePositionChangeResource(service, security);
    private final UUID employeeId = UUID.randomUUID();

    @Test
    void aRaiseWithoutTheCompensationPermissionIsForbidden() {
        when(security.hasPermission(PermissionsConstants.MANAGE_COMPENSATION)).thenReturn(false);
        PositionChangeRequest raise = new PositionChangeRequest(
            PositionChangeType.PROMOTION,
            LocalDate.now(),
            UUID.randomUUID(),
            null,
            new BigDecimal("11000"),
            "Strong year"
        );

        assertThatThrownBy(() -> resource.change(employeeId, raise)).isInstanceOf(AccessDeniedException.class);
        verify(service, never()).change(any(), any());
    }

    @Test
    void aPositionChangeWithoutPayNeedsNoCompensationPermission() {
        when(security.hasPermission(PermissionsConstants.MANAGE_COMPENSATION)).thenReturn(false);
        PositionChangeRequest move = new PositionChangeRequest(
            PositionChangeType.TRANSFER,
            LocalDate.now(),
            UUID.randomUUID(),
            null,
            null,
            "Reorg"
        );

        resource.change(employeeId, move);

        verify(service).change(employeeId, move);
    }

    @Test
    void theHistoryHidesPayWithoutTheCompensationPermission() {
        when(security.hasPermission(PermissionsConstants.MANAGE_COMPENSATION)).thenReturn(false);

        resource.history(employeeId);

        verify(service).history(employeeId, false);
    }
}
