package com.humano.service.me;

import com.humano.domain.shared.User;
import com.humano.dto.me.requests.ChangePasswordRequest;
import com.humano.dto.me.requests.UpdateMeRequest;
import com.humano.repository.shared.UserRepository;
import com.humano.security.SecurityUtils;
import com.humano.service.errors.InvalidPasswordException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Current-user (self) operations — fetch, update non-privileged fields,
 * change password. Every method resolves the principal from the
 * {@link SecurityUtils} accessors; no method here accepts a user identifier
 * as an argument. Admin operations on other users live in
 * {@code com.humano.service.admin.UserAccountService}.
 */
@Service
@Transactional
public class MeService {

    private static final Logger LOG = LoggerFactory.getLogger(MeService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public MeService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Load the current authenticated user with their authority set eagerly
     * fetched.
     */
    @Transactional(readOnly = true)
    public Optional<User> getCurrentUser() {
        return SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneWithAuthoritiesByLogin);
    }

    /**
     * Update the current user's non-privileged fields. Login, activation,
     * and authorities are intentionally absent from {@link UpdateMeRequest}
     * — those are admin operations.
     */
    public void updateCurrentUser(UpdateMeRequest request) {
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .ifPresent(user -> {
                user.setFirstName(request.firstName());
                user.setLastName(request.lastName());
                if (request.email() != null) {
                    user.setEmail(request.email().toLowerCase());
                }
                user.setLangKey(request.langKey());
                user.setImageUrl(request.imageUrl());
                userRepository.save(user);
                LOG.debug("Changed information for user: {}", user.getLogin());
            });
    }

    /**
     * Verify the current password against the stored hash, then write the
     * new one. Throws {@link InvalidPasswordException} on mismatch.
     */
    public void changePassword(ChangePasswordRequest request) {
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .ifPresent(user -> {
                if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                    throw new InvalidPasswordException();
                }
                user.setPassword(passwordEncoder.encode(request.newPassword()));
                LOG.debug("Changed password for user: {}", user.getLogin());
            });
    }
}
