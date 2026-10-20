package com.humano.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.PersistentToken;
import com.humano.domain.shared.User;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.PersistentTokenRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.billing.entitlement.EntitlementService;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.errors.PlanLimitException;
import com.humano.tenancy.TenantIteration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The plan's HR / payroll admin seats, as {@link UserAccountService#checkAdminSeat} applies them, and
 * switching sign-in off and on again when someone leaves or comes back.
 */
class AdminSeatLimitTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final EntitlementService entitlements = mock(EntitlementService.class);
    private final PersistentTokenRepository rememberedSignIns = mock(PersistentTokenRepository.class);
    private final UserAccountService service = new UserAccountService(
        userRepository,
        mock(AuthorityRepository.class),
        rememberedSignIns,
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

    private static User withRoles(String... roles) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setActivated(true);
        Set<Authority> authorities = new HashSet<>();
        for (String role : roles) {
            Authority authority = new Authority();
            authority.setName(role);
            authorities.add(authority);
        }
        user.setAuthorities(authorities);
        return user;
    }

    @Test
    void switchingOffSignInDeactivatesAndForgetsRememberedSignIns() {
        User leaver = withRoles(AuthoritiesConstants.EMPLOYEE);
        PersistentToken laptop = new PersistentToken();
        laptop.setSeries("laptop");
        when(rememberedSignIns.findByUser(leaver)).thenReturn(List.of(laptop));

        service.switchOffSignIn(leaver);

        assertThat(leaver.isActivated()).isFalse();
        verify(rememberedSignIns).deleteAll(List.of(laptop));
    }

    @Test
    void comingBackAsAnAdminNeedsAFreeSeat() {
        User admin = withRoles(AuthoritiesConstants.EMPLOYEE, AuthoritiesConstants.HR_MANAGER);
        admin.setActivated(false);
        when(userRepository.countActivatedHoldingAny(anyCollection())).thenReturn(3L);
        doThrow(PlanLimitException.limitReached(FeatureCodes.ADMIN_SEATS, 3, "admins", "Starter"))
            .when(entitlements)
            .checkOneMore(FeatureCodes.ADMIN_SEATS, 3L);

        assertThatThrownBy(() -> service.restoreSignIn(admin)).isInstanceOf(PlanLimitException.class);
        assertThat(admin.isActivated()).isFalse();
    }

    @Test
    void comingBackWithoutAnAdminRoleSwitchesSignInBackOn() {
        User employee = withRoles(AuthoritiesConstants.EMPLOYEE);
        employee.setActivated(false);

        service.restoreSignIn(employee);

        assertThat(employee.isActivated()).isTrue();
        verify(entitlements, never()).checkOneMore(any(), org.mockito.ArgumentMatchers.anyLong());
    }
}
