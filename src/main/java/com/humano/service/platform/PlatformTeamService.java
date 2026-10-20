package com.humano.service.platform;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import com.humano.dto.admin.requests.CreateUserRequest;
import com.humano.dto.platform.PlatformTeamMember;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.security.SecurityUtils;
import com.humano.service.MailService;
import com.humano.service.admin.UserAccountService;
import com.humano.service.errors.BadRequestAlertException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The platform's own staff (owners and admins), stored in the platform tenant — every
 * {@code /api/platform/**} request already runs there. Guards: only platform roles can be
 * granted, nobody changes or removes themselves, and the last active owner always stays.
 */
@Service
@Transactional("tenantTransactionManager")
public class PlatformTeamService {

    private static final String ENTITY = "platformTeam";
    /** Platform roles, most powerful first. */
    public static final List<String> ROLES = List.of(AuthoritiesConstants.PLATFORM_OWNER, AuthoritiesConstants.PLATFORM_ADMIN);

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final UserAccountService userAccountService;
    private final MailService mailService;

    public PlatformTeamService(
        UserRepository userRepository,
        AuthorityRepository authorityRepository,
        UserAccountService userAccountService,
        MailService mailService
    ) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.userAccountService = userAccountService;
        this.mailService = mailService;
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<PlatformTeamMember> list() {
        return userRepository
            .findAll()
            .stream()
            .map(u -> PlatformTeamMember.from(u, roleOf(u)))
            .sorted(Comparator.comparing((PlatformTeamMember m) -> ROLES.indexOf(m.role())).thenComparing(PlatformTeamMember::email))
            .toList();
    }

    /** Invites a teammate: the account is created and they get an email to set their password. */
    public PlatformTeamMember invite(String email, String firstName, String lastName, String role) {
        requirePlatformRole(role);
        String login = email.trim().toLowerCase();
        if (userRepository.findOneByEmailIgnoreCase(login).isPresent() || userRepository.findOneByLogin(login).isPresent()) {
            throw new BadRequestAlertException("Someone with this email is already on the team", ENTITY, "emailexists");
        }
        User user = userAccountService.createUser(
            new CreateUserRequest(
                login,
                blankToNull(firstName),
                blankToNull(lastName),
                login,
                null,
                null,
                Set.of(role, AuthoritiesConstants.USER)
            )
        );
        mailService.sendCreationEmail(user);
        return PlatformTeamMember.from(user, role);
    }

    /** Changes a teammate's role and/or whether they can sign in. */
    public PlatformTeamMember update(UUID id, String role, Boolean activated) {
        User user = find(id);
        requireNotSelf(user);
        String current = roleOf(user);
        if (role != null && !role.equals(current)) {
            requirePlatformRole(role);
            if (AuthoritiesConstants.PLATFORM_OWNER.equals(current)) requireAnotherOwner(user);
            Set<Authority> grants = new LinkedHashSet<>();
            authorityRepository.findById(role).ifPresent(grants::add);
            authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(grants::add);
            user.getAuthorities().clear();
            user.getAuthorities().addAll(grants);
            current = role;
        }
        if (activated != null && activated != user.isActivated()) {
            if (!activated && AuthoritiesConstants.PLATFORM_OWNER.equals(current)) requireAnotherOwner(user);
            user.setActivated(activated);
        }
        userRepository.save(user);
        return PlatformTeamMember.from(user, current);
    }

    /** Re-sends the invitation email to someone who hasn't set a password yet. */
    public void resendInvite(UUID id) {
        User user = find(id);
        if (user.getResetKey() == null) {
            throw new BadRequestAlertException("This person has already joined", ENTITY, "alreadyjoined");
        }
        mailService.sendCreationEmail(user);
    }

    public void remove(UUID id) {
        User user = find(id);
        requireNotSelf(user);
        if (AuthoritiesConstants.PLATFORM_OWNER.equals(roleOf(user))) requireAnotherOwner(user);
        userRepository.delete(user);
    }

    private User find(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new BadRequestAlertException("Team member not found", ENTITY, "notfound"));
    }

    private static String roleOf(User user) {
        return ROLES.stream().filter(r -> user.getAuthorities().stream().anyMatch(a -> a.getName().equals(r))).findFirst().orElse(null);
    }

    private static void requirePlatformRole(String role) {
        if (!ROLES.contains(role)) {
            throw new BadRequestAlertException("Not a platform role: " + role, ENTITY, "invalidrole");
        }
    }

    private static void requireNotSelf(User user) {
        if (SecurityUtils.getCurrentUserLogin().map(user.getLogin()::equalsIgnoreCase).orElse(false)) {
            throw new BadRequestAlertException("You can't change or remove your own access", ENTITY, "self");
        }
    }

    /** At least one other active owner must remain. */
    private void requireAnotherOwner(User user) {
        boolean another = userRepository
            .findAll()
            .stream()
            .anyMatch(
                u ->
                    !u.getId().equals(user.getId()) &&
                    u.isActivated() &&
                    u.getAuthorities().stream().anyMatch(a -> a.getName().equals(AuthoritiesConstants.PLATFORM_OWNER))
            );
        if (!another) {
            throw new BadRequestAlertException("The platform needs at least one active owner", ENTITY, "lastowner");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
