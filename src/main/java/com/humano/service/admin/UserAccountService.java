package com.humano.service.admin;

import com.humano.aop.audit.Auditable;
import com.humano.config.Constants;
import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import com.humano.dto.admin.requests.CreateUserRequest;
import com.humano.dto.admin.requests.UpdateUserRequest;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.PersistentTokenRepository;
import com.humano.repository.shared.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.security.RandomUtil;

/**
 * Admin-only and tenant-wide user operations — create, update, delete
 * arbitrary users; list managed users with full detail; list activatable
 * authorities; the user-directory read (any authenticated tenant member);
 * scheduled cleanup of stale persistent tokens and never-activated
 * registrations.
 * <p>
 * Self-service flows live in {@code com.humano.service.me.MeService};
 * anonymous auth flows in
 * {@code com.humano.service.auth.UserRegistrationService}.
 */
@Service
@Transactional
public class UserAccountService {

    private static final Logger LOG = LoggerFactory.getLogger(UserAccountService.class);

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PersistentTokenRepository persistentTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(
        UserRepository userRepository,
        AuthorityRepository authorityRepository,
        PersistentTokenRepository persistentTokenRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.persistentTokenRepository = persistentTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Create a user with admin-supplied attributes. A random password is
     * generated; the caller is expected to send the activation/reset email
     * so the recipient can set their own password.
     */
    public User createUser(CreateUserRequest request) {
        User user = new User();
        user.setLogin(request.login().toLowerCase());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        if (request.email() != null) {
            user.setEmail(request.email().toLowerCase());
        }
        user.setImageUrl(request.imageUrl());
        user.setLangKey(request.langKey() == null ? Constants.DEFAULT_LANGUAGE : request.langKey());
        user.setPassword(passwordEncoder.encode(RandomUtil.generatePassword()));
        user.setResetKey(RandomUtil.generateResetKey());
        user.setResetDate(Instant.now());
        user.setActivated(true);
        if (request.authorities() != null) {
            Set<Authority> authorities = request
                .authorities()
                .stream()
                .map(authorityRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
            user.setAuthorities(authorities);
        }
        userRepository.save(user);
        LOG.debug("Created user: {}", user.getLogin());
        return user;
    }

    /**
     * Replace all admin-controlled fields on an existing user. The
     * {@link UpdateUserRequest#authorities} set is a full replacement —
     * authorities not in the set are removed.
     */
    @Auditable(action = "USER_UPDATED", targetType = "User", targetIdExpression = "#request.login")
    public Optional<User> updateUser(UpdateUserRequest request) {
        return userRepository
            .findById(request.id())
            .map(user -> {
                user.setLogin(request.login().toLowerCase());
                user.setFirstName(request.firstName());
                user.setLastName(request.lastName());
                if (request.email() != null) {
                    user.setEmail(request.email().toLowerCase());
                }
                user.setImageUrl(request.imageUrl());
                user.setActivated(request.activated());
                user.setLangKey(request.langKey());
                Set<Authority> managedAuthorities = user.getAuthorities();
                managedAuthorities.clear();
                if (request.authorities() != null) {
                    request
                        .authorities()
                        .stream()
                        .map(authorityRepository::findById)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .forEach(managedAuthorities::add);
                }
                userRepository.save(user);
                LOG.debug("Changed information for user: {}", user.getLogin());
                return user;
            });
    }

    public void deleteUser(String login) {
        userRepository
            .findOneByLogin(login)
            .ifPresent(user -> {
                userRepository.delete(user);
                LOG.debug("Deleted user: {}", user.getLogin());
            });
    }

    @Transactional(readOnly = true)
    public Page<User> getAllManagedUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<User> getPublicDirectory(Pageable pageable) {
        return userRepository.findAllByIdNotNullAndActivatedIsTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserWithAuthoritiesByLogin(String login) {
        return userRepository.findOneWithAuthoritiesByLogin(login);
    }

    @Transactional(readOnly = true)
    public List<String> getAuthorities() {
        return authorityRepository.findAll().stream().map(Authority::getName).toList();
    }

    /**
     * Daily cleanup of persistent-token rows older than 30 days. JHipster's
     * "remember me" cookies hand out one row per session; without pruning
     * the table grows unbounded.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void removeOldPersistentTokens() {
        LocalDate now = LocalDate.now();
        persistentTokenRepository
            .findByTokenDateBefore(now.minusMonths(1))
            .forEach(token -> {
                LOG.debug("Deleting persistent token {}", token.getSeries());
                User user = token.getUser();
                user.getPersistentTokens().remove(token);
                persistentTokenRepository.delete(token);
            });
    }

    /**
     * Daily cleanup of users who registered but never activated within
     * three days. Keeps the user table free of half-abandoned signups.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void removeNotActivatedUsers() {
        userRepository
            .findAllByActivatedIsFalseAndActivationKeyIsNotNullAndCreatedDateBefore(Instant.now().minus(3, ChronoUnit.DAYS))
            .forEach(user -> {
                LOG.debug("Deleting never-activated user {}", user.getLogin());
                userRepository.delete(user);
            });
    }
}
