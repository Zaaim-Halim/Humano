package com.humano.web.rest.auth;

import com.humano.config.Constants;
import com.humano.domain.shared.User;
import com.humano.dto.auth.requests.RequestPasswordResetRequest;
import com.humano.dto.auth.requests.ResetPasswordRequest;
import com.humano.security.annotation.PublicEndpoint;
import com.humano.service.MailService;
import com.humano.service.auth.UserRegistrationService;
import com.humano.web.rest.errors.InvalidPasswordException;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous auth-flow endpoints — registration, activation, password reset,
 * "am I logged in?" check. Every endpoint here is reachable without a
 * principal; the security configuration permits them by URL and the
 * {@link PublicEndpoint} annotation documents that intent at the method
 * site.
 * <p>
 * URLs are preserved from the JHipster baseline so existing clients
 * (web SPA, mobile, partner integrations) keep working through the
 * refactor.
 */
@RestController
@RequestMapping("/api")
public class AuthResource {

    private static final Logger LOG = LoggerFactory.getLogger(AuthResource.class);

    private final UserRegistrationService registrationService;
    private final MailService mailService;

    public AuthResource(UserRegistrationService registrationService, MailService mailService) {
        this.registrationService = registrationService;
        this.mailService = mailService;
    }

    /**
     * Consume an activation key emailed to the registrant.
     */
    @GetMapping("/activate")
    @PublicEndpoint
    public void activateAccount(@RequestParam(value = "key") String key) {
        Optional<User> user = registrationService.activateRegistration(key);
        if (user.isEmpty()) {
            throw new AuthResourceException("No user was found for this activation key");
        }
    }

    /**
     * Liveness probe used by SPAs to determine whether the session cookie
     * is still valid. Returns 204 when authenticated, 401 otherwise — both
     * via {@code Principal} resolution by Spring Security.
     */
    @GetMapping("/authenticate")
    @PublicEndpoint
    public ResponseEntity<Void> isAuthenticated(Principal principal) {
        LOG.debug("REST request to check if the current user is authenticated");
        return ResponseEntity.status(principal == null ? HttpStatus.UNAUTHORIZED : HttpStatus.NO_CONTENT).build();
    }

    /**
     * Initiate a password reset by emailing a reset key. Responds 200 even
     * when the email is unknown — leaking presence would let an attacker
     * enumerate registered emails.
     */
    @PostMapping("/account/reset-password/init")
    @PublicEndpoint
    public void requestPasswordReset(@Valid @RequestBody RequestPasswordResetRequest request) {
        Optional<User> user = registrationService.requestPasswordReset(request.email());
        if (user.isPresent()) {
            mailService.sendPasswordResetMail(user.orElseThrow());
        } else {
            LOG.warn("Password reset requested for non-existent or inactive email");
        }
    }

    /**
     * Consume a reset key + the new password.
     */
    @PostMapping("/account/reset-password/finish")
    @PublicEndpoint
    public void finishPasswordReset(@Valid @RequestBody ResetPasswordRequest request) {
        if (isPasswordLengthInvalid(request.newPassword())) {
            throw new InvalidPasswordException();
        }
        Optional<User> user = registrationService.completePasswordReset(request.newPassword(), request.key());
        if (user.isEmpty()) {
            throw new AuthResourceException("No user was found for this reset key");
        }
    }

    private static boolean isPasswordLengthInvalid(String password) {
        if (password == null) {
            return true;
        }
        return password.length() < Constants.PASSWORD_MIN_LENGTH || password.length() > Constants.PASSWORD_MAX_LENGTH;
    }

    private static class AuthResourceException extends RuntimeException {

        private AuthResourceException(String message) {
            super(message);
        }
    }
}
