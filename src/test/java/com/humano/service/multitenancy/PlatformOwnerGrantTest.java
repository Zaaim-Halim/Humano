package com.humano.service.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for the security-sensitive platform-owner grant policy used by the startup
 * backfill ({@code TenantInitializationService#applyPlatformOwnerGrant}). The caller bounds
 * this to the platform tenant; these tests pin the grant policy itself: additive, only for
 * existing admins, never duplicated.
 */
class PlatformOwnerGrantTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    private TenantInitializationService newService() {
        // Only userRepository is exercised by applyPlatformOwnerGrant; the rest are unused here.
        return new TenantInitializationService(null, null, userRepository, null, null, null, null, null, null, null);
    }

    private static Authority authority(String name) {
        Authority a = new Authority();
        a.setName(name);
        return a;
    }

    private static User userWith(String login, String... authorityNames) {
        User user = new User();
        user.setLogin(login);
        Set<Authority> authorities = new HashSet<>();
        for (String name : authorityNames) {
            authorities.add(authority(name));
        }
        user.setAuthorities(authorities);
        return user;
    }

    @Test
    void grantsOwnerToAdminWithoutIt() {
        User admin = userWith("admin", AuthoritiesConstants.ADMIN);
        Authority ownerRole = authority(AuthoritiesConstants.PLATFORM_OWNER);

        int granted = newService().applyPlatformOwnerGrant(List.of(admin), ownerRole);

        assertThat(granted).isEqualTo(1);
        assertThat(admin.getAuthorities()).extracting(Authority::getName).contains(AuthoritiesConstants.PLATFORM_OWNER);
        verify(userRepository).save(admin);
    }

    @Test
    void skipsNonAdminUsers() {
        User employee = userWith("emp", AuthoritiesConstants.EMPLOYEE);
        Authority ownerRole = authority(AuthoritiesConstants.PLATFORM_OWNER);

        int granted = newService().applyPlatformOwnerGrant(List.of(employee), ownerRole);

        assertThat(granted).isZero();
        assertThat(employee.getAuthorities()).extracting(Authority::getName).doesNotContain(AuthoritiesConstants.PLATFORM_OWNER);
        verify(userRepository, never()).save(employee);
    }

    @Test
    void doesNotRegrantWhenAlreadyOwner() {
        User owner = userWith("owner", AuthoritiesConstants.ADMIN, AuthoritiesConstants.PLATFORM_OWNER);
        Authority ownerRole = authority(AuthoritiesConstants.PLATFORM_OWNER);

        int granted = newService().applyPlatformOwnerGrant(List.of(owner), ownerRole);

        assertThat(granted).isZero();
        verify(userRepository, never()).save(owner);
    }

    @Test
    void noOwnerRoleMeansNoGrants() {
        User admin = userWith("admin", AuthoritiesConstants.ADMIN);

        int granted = newService().applyPlatformOwnerGrant(List.of(admin), null);

        assertThat(granted).isZero();
        verify(userRepository, never()).save(admin);
    }

    @Test
    void grantsOnlyToAdminsInMixedSet() {
        User admin = userWith("admin", AuthoritiesConstants.ADMIN);
        User employee = userWith("emp", AuthoritiesConstants.EMPLOYEE);
        User alreadyOwner = userWith("owner", AuthoritiesConstants.ADMIN, AuthoritiesConstants.PLATFORM_OWNER);
        Authority ownerRole = authority(AuthoritiesConstants.PLATFORM_OWNER);

        int granted = newService().applyPlatformOwnerGrant(List.of(admin, employee, alreadyOwner), ownerRole);

        assertThat(granted).isEqualTo(1);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getLogin()).isEqualTo("admin");
    }
}
