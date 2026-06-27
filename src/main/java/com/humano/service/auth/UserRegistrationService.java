package com.humano.service.auth;

import com.humano.domain.shared.User;
import com.humano.repository.shared.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
}
