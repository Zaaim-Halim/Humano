package com.humano.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.platform.PlatformOwnerBootstrap.Result;
import com.humano.tenancy.MultiTenantProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

class PlatformOwnerBootstrapTest {

    private static final String EMAIL = "Founder@Example.com";
    private static final String PASSWORD = "a-long-unique-password";

    private final UserRepository users = mock(UserRepository.class);
    private final AuthorityRepository authorities = mock(AuthorityRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final Authority owner = authority(AuthoritiesConstants.PLATFORM_OWNER);

    @BeforeEach
    void setUp() {
        when(authorities.findById(AuthoritiesConstants.PLATFORM_OWNER)).thenReturn(Optional.of(owner));
        when(authorities.findById(AuthoritiesConstants.USER)).thenReturn(Optional.of(authority(AuthoritiesConstants.USER)));
        when(encoder.encode(PASSWORD)).thenReturn("hashed");
        when(users.findAll()).thenReturn(List.of());
        when(users.findOneByLogin(any())).thenReturn(Optional.empty());
    }

    private PlatformOwnerBootstrap bootstrap(String email, String password) {
        MultiTenantProperties properties = new MultiTenantProperties();
        properties.setPlatformTenant("default");
        return new PlatformOwnerBootstrap(users, authorities, encoder, mock(PlatformTransactionManager.class), properties, email, password);
    }

    private static Authority authority(String name) {
        Authority a = new Authority();
        a.setName(name);
        return a;
    }

    @Test
    void doesNothingWhenNotConfigured() {
        assertThat(bootstrap("", "").run()).isEqualTo(Result.NOT_CONFIGURED);
        verify(users, never()).save(any());
    }

    @Test
    void createsTheFirstOwnerWithTheConfiguredPassword() {
        assertThat(bootstrap(EMAIL, PASSWORD).run()).isEqualTo(Result.CREATED);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getLogin()).isEqualTo("founder@example.com");
        assertThat(user.getEmail()).isEqualTo("founder@example.com");
        assertThat(user.getPassword()).isEqualTo("hashed");
        assertThat(user.isActivated()).isTrue();
        assertThat(user.getAuthorities())
            .extracting(Authority::getName)
            .containsExactlyInAnyOrder(AuthoritiesConstants.PLATFORM_OWNER, AuthoritiesConstants.USER);
    }

    @Test
    void isIgnoredOnceAnOwnerExists() {
        // A left-behind password must never reset an owner's password or add a second owner.
        User existing = new User();
        existing.setLogin("someone@example.com");
        existing.setAuthorities(new HashSet<>(Set.of(owner)));
        when(users.findAll()).thenReturn(List.of(existing));

        assertThat(bootstrap(EMAIL, PASSWORD).run()).isEqualTo(Result.OWNER_EXISTS);
        verify(users, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void promotesAnExistingAccountWithoutChangingItsPassword() {
        User existing = new User();
        existing.setLogin("founder@example.com");
        existing.setPassword("their-own-hash");
        existing.setAuthorities(new HashSet<>());
        when(users.findOneByLogin("founder@example.com")).thenReturn(Optional.of(existing));

        assertThat(bootstrap(EMAIL, PASSWORD).run()).isEqualTo(Result.PROMOTED);
        assertThat(existing.getAuthorities()).contains(owner);
        assertThat(existing.getPassword()).isEqualTo("their-own-hash");
    }

    @Test
    void refusesUnsafeOrIncompleteValues() {
        assertThat(bootstrap(EMAIL, "").run()).isEqualTo(Result.INVALID);
        assertThat(bootstrap(EMAIL, "short-pass").run()).isEqualTo(Result.INVALID);
        assertThat(bootstrap("not-an-email", PASSWORD).run()).isEqualTo(Result.INVALID);
        verify(users, never()).save(any());
    }

    @Test
    void validatesEmailAndPasswordLength() {
        assertThat(PlatformOwnerBootstrap.validate("founder@example.com", PASSWORD)).isNull();
        assertThat(PlatformOwnerBootstrap.validate("founder@example.com", "x".repeat(11))).contains("12 to 100");
        assertThat(PlatformOwnerBootstrap.validate("founder@example.com", "x".repeat(101))).contains("12 to 100");
        assertThat(PlatformOwnerBootstrap.validate("a".repeat(45) + "@x.com", PASSWORD)).contains("50 characters");
    }
}
