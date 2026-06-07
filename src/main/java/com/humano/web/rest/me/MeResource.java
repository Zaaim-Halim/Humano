package com.humano.web.rest.me;

import com.humano.config.Constants;
import com.humano.domain.shared.PersistentToken;
import com.humano.domain.shared.User;
import com.humano.dto.me.requests.ChangePasswordRequest;
import com.humano.dto.me.requests.UpdateMeRequest;
import com.humano.dto.me.responses.MeResponse;
import com.humano.repository.shared.PersistentTokenRepository;
import com.humano.repository.shared.UserRepository;
import com.humano.security.SecurityUtils;
import com.humano.security.annotation.RequireAuthenticated;
import com.humano.service.me.MeService;
import com.humano.web.rest.errors.EmailAlreadyUsedException;
import com.humano.web.rest.errors.InvalidPasswordException;
import jakarta.validation.Valid;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current-user (self) endpoints. Every endpoint here requires an
 * authenticated principal and acts on that principal's record only — there
 * is no path variable identifying a different user. Admin operations on
 * other users live in
 * {@link com.humano.web.rest.admin.UserAdminResource}.
 * <p>
 * URLs are preserved from the JHipster baseline (still under
 * {@code /api/account/...}) so clients keep working. The naming choice for
 * the internal split ({@code me/}) signals intent without breaking wire
 * compatibility.
 */
@RestController
@RequestMapping("/api")
public class MeResource {

    private static final Logger LOG = LoggerFactory.getLogger(MeResource.class);

    private final MeService meService;
    private final UserRepository userRepository;
    private final PersistentTokenRepository persistentTokenRepository;

    public MeResource(MeService meService, UserRepository userRepository, PersistentTokenRepository persistentTokenRepository) {
        this.meService = meService;
        this.userRepository = userRepository;
        this.persistentTokenRepository = persistentTokenRepository;
    }

    /**
     * Get the current user's profile + authorities.
     */
    @GetMapping("/account")
    @RequireAuthenticated
    public MeResponse getAccount() {
        return meService
            .getCurrentUser()
            .map(MeResponse::fromUser)
            .orElseThrow(() -> new MeResourceException("Current user could not be found"));
    }

    /**
     * Update the current user's non-privileged fields. The email-conflict
     * check is here (not in the service) because it requires a controller-
     * side fetch keyed by login to know "is this email taken by someone
     * else?".
     */
    @PostMapping("/account")
    @RequireAuthenticated
    public void saveAccount(@Valid @RequestBody UpdateMeRequest request) {
        String userLogin = SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new MeResourceException("Current user login not found"));
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(request.email());
        if (existingUser.isPresent() && !existingUser.orElseThrow().getLogin().equalsIgnoreCase(userLogin)) {
            throw new EmailAlreadyUsedException();
        }
        meService.updateCurrentUser(request);
    }

    /**
     * Change the current user's password — requires the current password
     * to match.
     */
    @PostMapping("/account/change-password")
    @RequireAuthenticated
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        if (isPasswordLengthInvalid(request.newPassword())) {
            throw new InvalidPasswordException();
        }
        meService.changePassword(request);
    }

    /**
     * List the current user's persistent ("remember me") sessions.
     */
    @GetMapping("/account/sessions")
    @RequireAuthenticated
    public List<PersistentToken> getCurrentSessions() {
        User user = userRepository
            .findOneByLogin(SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new MeResourceException("Current user login not found")))
            .orElseThrow(() -> new MeResourceException("Current user could not be found"));
        return persistentTokenRepository.findByUser(user);
    }

    /**
     * Invalidate one of the current user's sessions by series id.
     * Idempotent — invalidating an already-invalidated series is a no-op.
     */
    @DeleteMapping("/account/sessions/{series}")
    @RequireAuthenticated
    public void invalidateSession(@PathVariable("series") String series) {
        String decodedSeries = URLDecoder.decode(series, StandardCharsets.UTF_8);
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .ifPresent(user ->
                persistentTokenRepository
                    .findByUser(user)
                    .stream()
                    .filter(persistentToken -> StringUtils.equals(persistentToken.getSeries(), decodedSeries))
                    .findAny()
                    .ifPresent(t -> persistentTokenRepository.deleteById(decodedSeries))
            );
    }

    private static boolean isPasswordLengthInvalid(String password) {
        if (password == null) {
            return true;
        }
        return password.length() < Constants.PASSWORD_MIN_LENGTH || password.length() > Constants.PASSWORD_MAX_LENGTH;
    }

    private static class MeResourceException extends RuntimeException {

        private MeResourceException(String message) {
            super(message);
        }
    }
}
