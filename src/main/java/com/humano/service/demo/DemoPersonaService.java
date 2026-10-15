package com.humano.service.demo;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The persona logins of a sample company: one activated user per role in the tenant's authority
 * catalog ({@code <role>@demo.humano}, each holding that role plus {@code ROLE_USER}), all sharing
 * one password. {@link DemoDataSeeder} then turns them into the company's key people.
 */
@Service
public class DemoPersonaService {

    private static final Logger log = LoggerFactory.getLogger(DemoPersonaService.class);

    /** The persona logins' email domain. */
    public static final String EMAIL_DOMAIN = "demo.humano";

    private final AuthorityRepository authorityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tenantTx;

    public DemoPersonaService(
        AuthorityRepository authorityRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTransactionManager
    ) {
        this.authorityRepository = authorityRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantTx = new TransactionTemplate(tenantTransactionManager);
    }

    /**
     * Creates the persona logins in the current tenant (each in its own tx; existing logins are
     * skipped). Returns every persona login, created now or before. Must run with the tenant set in
     * {@code TenantContext}.
     */
    public List<String> createPersonas(String password) {
        // Capture role NAMES only (plain strings) — the Authority entities are re-fetched inside each
        // per-user tx so they stay managed when attached to the new user.
        List<String> roleNames = tenantTx.execute(status -> authorityRepository.findAll().stream().map(Authority::getName).toList());
        List<String> logins = new ArrayList<>();
        if (roleNames == null) {
            return logins;
        }
        String encoded = passwordEncoder.encode(password);
        for (String role : roleNames) {
            if (AuthoritiesConstants.USER.equals(role) || AuthoritiesConstants.ANONYMOUS.equals(role)) {
                continue; // baseline roles, not personas
            }
            try {
                tenantTx.executeWithoutResult(status -> createPersona(role, encoded));
                logins.add(emailForRole(role));
            } catch (Exception e) {
                log.warn("[demo-personas] skipped persona for role {} ({})", role, e.getMessage());
            }
        }
        return logins;
    }

    /** Runs inside a per-user tenant transaction so the looked-up authorities are managed. */
    private void createPersona(String roleName, String encodedPassword) {
        String login = emailForRole(roleName);
        if (userRepository.findOneByLogin(login).isPresent()) {
            return; // idempotent: already created (covers the provisioning-created admin)
        }
        Authority authority = authorityRepository.findById(roleName).orElse(null);
        if (authority == null) {
            return;
        }
        User user = new User();
        user.setLogin(login);
        user.setEmail(login);
        user.setFirstName("Demo");
        user.setLastName(labelForRole(roleName));
        user.setPassword(encodedPassword);
        user.setActivated(true);
        user.setLangKey("en");
        Set<Authority> grants = new LinkedHashSet<>();
        grants.add(authority);
        authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(grants::add);
        user.setAuthorities(grants);
        userRepository.save(user);
        log.info("[demo-personas] created {}  (role {})", login, roleName);
    }

    /** {@code ROLE_HR_MANAGER} → {@code hr.manager@demo.humano}. */
    public static String emailForRole(String role) {
        return role.replaceFirst("^ROLE_", "").toLowerCase().replace('_', '.') + "@" + EMAIL_DOMAIN;
    }

    /** {@code ROLE_HR_MANAGER} → {@code Hr Manager}. */
    static String labelForRole(String role) {
        String[] parts = role.replaceFirst("^ROLE_", "").toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
