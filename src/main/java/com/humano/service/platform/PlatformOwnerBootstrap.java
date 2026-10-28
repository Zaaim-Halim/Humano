package com.humano.service.platform;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.tenancy.MultiTenantProperties;
import com.humano.tenancy.TenantContext;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the first platform console owner on a new installation, where no user exists yet (development
 * gets one from the dev seeder). The operator sets {@code HUMANO_OWNER_EMAIL} and
 * {@code HUMANO_OWNER_PASSWORD}, starts the app once, signs in with the platform tenant as organization,
 * changes the password, and removes both variables.
 * <p>
 * It acts only while the platform tenant has no {@link AuthoritiesConstants#PLATFORM_OWNER}: once an owner
 * exists the variables are ignored (and a warning asks to remove a password left behind), so they can
 * never reset a password or add a second owner later. Run by {@code StartupTenantMigrator} right after the
 * platform roles are seeded, which the owner role needs.
 */
@Component
public class PlatformOwnerBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformOwnerBootstrap.class);

    /** As long as the passwords people choose themselves would need to be, and then some: this one sits in an environment variable. */
    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tenantTx;
    private final String platformTenant;
    private final String email;
    private final String password;

    public PlatformOwnerBootstrap(
        UserRepository userRepository,
        AuthorityRepository authorityRepository,
        PasswordEncoder passwordEncoder,
        @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTransactionManager,
        MultiTenantProperties multiTenantProperties,
        @Value("${humano.platform.owner.email:}") String email,
        @Value("${humano.platform.owner.password:}") String password
    ) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantTx = new TransactionTemplate(tenantTransactionManager);
        this.platformTenant = multiTenantProperties.getPlatformTenant();
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
    }

    /** The outcome, for the log and the tests. */
    enum Result {
        NOT_CONFIGURED,
        OWNER_EXISTS,
        INVALID,
        CREATED,
        PROMOTED,
    }

    public Result run() {
        if (email.isEmpty() && password.isEmpty()) {
            return Result.NOT_CONFIGURED;
        }
        String previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(platformTenant);
        try {
            return tenantTx.execute(status -> bootstrap());
        } finally {
            if (previous != null) {
                TenantContext.setCurrentTenant(previous);
            } else {
                TenantContext.clear();
            }
        }
    }

    private Result bootstrap() {
        boolean ownerExists = userRepository
            .findAll()
            .stream()
            .anyMatch(u -> u.getAuthorities().stream().anyMatch(a -> AuthoritiesConstants.PLATFORM_OWNER.equals(a.getName())));
        if (ownerExists) {
            if (!password.isEmpty()) {
                LOG.warn("A platform console owner already exists: HUMANO_OWNER_PASSWORD is ignored. Remove it from the environment.");
            }
            return Result.OWNER_EXISTS;
        }
        String problem = validate(email, password);
        if (problem != null) {
            LOG.error("No platform console owner was created: {}", problem);
            return Result.INVALID;
        }
        Authority owner = authorityRepository.findById(AuthoritiesConstants.PLATFORM_OWNER).orElse(null);
        Authority user = authorityRepository.findById(AuthoritiesConstants.USER).orElse(null);
        if (owner == null) {
            LOG.error(
                "No platform console owner was created: the {} role is missing in tenant '{}'",
                AuthoritiesConstants.PLATFORM_OWNER,
                platformTenant
            );
            return Result.INVALID;
        }
        User existing = userRepository.findOneByLogin(email).orElse(null);
        if (existing != null) {
            // An account the operator already made (say, before this existed): make it the owner, keep its password.
            existing.getAuthorities().add(owner);
            userRepository.save(existing);
            LOG.warn(
                "Made '{}' the platform console owner (organization '{}'); its password is unchanged. Remove HUMANO_OWNER_EMAIL and HUMANO_OWNER_PASSWORD from the environment.",
                email,
                platformTenant
            );
            return Result.PROMOTED;
        }
        User account = new User();
        account.setLogin(email);
        account.setEmail(email);
        account.setFirstName("Platform");
        account.setLastName("Owner");
        account.setPassword(passwordEncoder.encode(password));
        account.setActivated(true);
        account.setLangKey("en");
        Set<Authority> grants = new LinkedHashSet<>();
        grants.add(owner);
        if (user != null) {
            grants.add(user);
        }
        account.setAuthorities(grants);
        userRepository.save(account);
        LOG.warn(
            "Created the platform console owner '{}'. Sign in with organization '{}', change the password under Account, then remove HUMANO_OWNER_EMAIL and HUMANO_OWNER_PASSWORD from the environment.",
            email,
            platformTenant
        );
        return Result.CREATED;
    }

    /** What is wrong with the configured values, or null when they can be used. */
    static String validate(String email, String password) {
        if (email.isEmpty() || password.isEmpty()) {
            return "set both HUMANO_OWNER_EMAIL and HUMANO_OWNER_PASSWORD";
        }
        if (email.length() > 50 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return "HUMANO_OWNER_EMAIL is not a valid email address of at most 50 characters";
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > 100) {
            return "HUMANO_OWNER_PASSWORD must be " + MIN_PASSWORD_LENGTH + " to 100 characters long";
        }
        return null;
    }
}
