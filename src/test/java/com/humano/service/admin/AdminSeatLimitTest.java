package com.humano.service.admin;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.config.multitenancy.TenantIteration;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.PersistentTokenRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.billing.entitlement.EntitlementService;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.web.rest.errors.PlanLimitException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The plan's HR / payroll admin seats, as {@link UserAccountService#checkAdminSeat} applies them. */
class AdminSeatLimitTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final EntitlementService entitlements = mock(EntitlementService.class);
    private final UserAccountService service = new UserAccountService(
        userRepository,
        mock(AuthorityRepository.class),
        mock(PersistentTokenRepository.class),
        mock(PasswordEncoder.class),
        mock(TenantIteration.class),
        new SimpleMeterRegistry(),
        entitlements
    );

    @Test
    void newAdminIsCheckedAgainstTheSeatsInUse() {
        when(userRepository.countActivatedHoldingAny(anyCollection())).thenReturn(3L);
        doThrow(PlanLimitException.limitReached(FeatureCodes.ADMIN_SEATS, 3, "admins", "Starter"))
            .when(entitlements)
            .checkOneMore(FeatureCodes.ADMIN_SEATS, 3L);

        assertThatThrownBy(() -> service.checkAdminSeat(null, Set.of(AuthoritiesConstants.HR_MANAGER), true)).isInstanceOf(
            PlanLimitException.class
        );
    }

    @Test
    void someoneWhoAlreadyHoldsASeatKeepsIt() {
        UUID id = UUID.randomUUID();
        when(userRepository.holdsActivatedAny(eq(id), anyCollection())).thenReturn(true);

        assertThatCode(() -> service.checkAdminSeat(id, Set.of(AuthoritiesConstants.PAYROLL_ADMIN), true)).doesNotThrowAnyException();
        verify(entitlements, never()).checkOneMore(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void employeesAndDeactivatedPeopleTakeNoSeat() {
        service.checkAdminSeat(null, Set.of(AuthoritiesConstants.EMPLOYEE, AuthoritiesConstants.MANAGER), true);
        service.checkAdminSeat(null, Set.of(AuthoritiesConstants.ADMIN), false);
        verify(entitlements, never()).checkOneMore(any(), org.mockito.ArgumentMatchers.anyLong());
    }
}
