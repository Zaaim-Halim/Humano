package com.humano.service.auth;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import com.humano.dto.auth.requests.RegisterUserRequest;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.errors.EmailAlreadyUsedException;
import com.humano.service.errors.UsernameAlreadyUsedException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.security.RandomUtil;

/**
 * Anonymous auth-flow operations — self-registration, activation, password
 * reset. Every method here is reachable from a public endpoint (no
 * authenticated principal); separating them from current-user and
 * admin-only paths keeps the security review surface small.
 */
@Service
@Transactional
public class UserRegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(UserRegistrationService.class);

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(
        UserRepository userRepository,
        AuthorityRepository authorityRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Self-registration. The new user is inactive — they must follow the
     * activation link emailed to them before they can log in. Re-registering
     * an existing not-yet-activated login/email silently replaces the prior
     * registration; an activated collision throws.
     */
    public User registerUser(RegisterUserRequest request) {
        userRepository
            .findOneByLogin(request.login().toLowerCase())
            .ifPresent(existingUser -> {
                if (!removeNonActivatedUser(existingUser)) {
                    throw new UsernameAlreadyUsedException();
                }
            });
        userRepository
            .findOneByEmailIgnoreCase(request.email())
            .ifPresent(existingUser -> {
                if (!removeNonActivatedUser(existingUser)) {
                    throw new EmailAlreadyUsedException();
                }
            });

        User newUser = new User();
        newUser.setLogin(request.login().toLowerCase());
        newUser.setPassword(passwordEncoder.encode(request.password()));
        newUser.setFirstName(request.firstName());
        newUser.setLastName(request.lastName());
        newUser.setEmail(request.email().toLowerCase());
        newUser.setLangKey(request.langKey());
        newUser.setActivated(false);
        newUser.setActivationKey(RandomUtil.generateActivationKey());

        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
        newUser.setAuthorities(authorities);

        userRepository.save(newUser);
        LOG.debug("Registered new user (inactive): {}", newUser.getLogin());
        return newUser;
    }

    /**
     * Consume an activation key emailed to the registrant. Idempotent on a
     * successful activation: the key is wiped, so a replay is a no-op
     * (returns empty).
     */
    public Optional<User> activateRegistration(String key) {
        LOG.debug("Activating user for activation key {}", key);
        return userRepository
            .findOneByActivationKey(key)
            .map(user -> {
                user.setActivated(true);
                user.setActivationKey(null);
                return user;
            });
    }

    /**
     * Issue a reset key for the email's account. Returns empty when the
     * email is unknown or unactivated — the caller responds identically
     * either way to avoid leaking account presence.
     */
    public Optional<User> requestPasswordReset(String mail) {
        return userRepository
            .findOneByEmailIgnoreCase(mail)
            .filter(User::isActivated)
            .map(user -> {
                user.setResetKey(RandomUtil.generateResetKey());
                user.setResetDate(Instant.now());
                return user;
            });
    }

    /**
     * Consume a reset key within its 24-hour window. The key is wiped on
     * success.
     */
    public Optional<User> completePasswordReset(String newPassword, String key) {
        LOG.debug("Reset user password for reset key {}", key);
        return userRepository
            .findOneByResetKey(key)
            .filter(user -> user.getResetDate().isAfter(Instant.now().minus(1, ChronoUnit.DAYS)))
            .map(user -> {
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setResetKey(null);
                user.setResetDate(null);
                return user;
            });
    }

    private boolean removeNonActivatedUser(User existingUser) {
        if (existingUser.isActivated()) {
            return false;
        }
        userRepository.delete(existingUser);
        userRepository.flush();
        return true;
    }
}
