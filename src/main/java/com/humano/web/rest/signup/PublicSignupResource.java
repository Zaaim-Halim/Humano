package com.humano.web.rest.signup;

import com.humano.dto.signup.CompleteSignupRequest;
import com.humano.dto.signup.SignupLinkRequest;
import com.humano.dto.signup.SignupView;
import com.humano.dto.signup.StartSignupRequest;
import com.humano.dto.signup.SubdomainCheck;
import com.humano.security.annotation.PublicEndpoint;
import com.humano.service.signup.SignupRateLimiter;
import com.humano.service.signup.SignupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public site's self-serve sign-up (no sign-in): check a workspace address, ask for the
 * confirmation email, then — from the emailed link — confirm, start the setup and follow it. Every
 * call is rate-limited per caller. The link's token travels in request bodies, never in URLs.
 */
@RestController
@RequestMapping("/api/public/signup")
@PublicEndpoint
public class PublicSignupResource {

    private final SignupService signupService;
    private final SignupRateLimiter rateLimiter;

    public PublicSignupResource(SignupService signupService, SignupRateLimiter rateLimiter) {
        this.signupService = signupService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/subdomains/{subdomain}")
    public SubdomainCheck checkSubdomain(@PathVariable String subdomain, HttpServletRequest http) {
        rateLimiter.check("check:" + http.getRemoteAddr(), 60, Duration.ofMinutes(1));
        return signupService.checkSubdomain(subdomain);
    }

    /** Emails the confirmation link. Always 202 when the form is valid: it never says whether the address has a workspace. */
    @PostMapping
    public ResponseEntity<Void> start(@Valid @RequestBody StartSignupRequest request, HttpServletRequest http) {
        rateLimiter.check("start:" + http.getRemoteAddr(), 5, Duration.ofMinutes(15));
        rateLimiter.check("start:" + request.email().trim().toLowerCase(), 3, Duration.ofHours(1));
        signupService.start(request);
        return ResponseEntity.accepted().build();
    }

    /** The emailed link: the sign-up and how its setup is going (polled while the workspace is set up). */
    @PostMapping("/link")
    public SignupView link(@Valid @RequestBody SignupLinkRequest request, HttpServletRequest http) {
        rateLimiter.check("link:" + http.getRemoteAddr(), 120, Duration.ofMinutes(5));
        return signupService.view(request.token());
    }

    /** Confirms the email, sets the admin's name and password, and starts (or retries) the setup. */
    @PostMapping("/complete")
    public SignupView complete(@Valid @RequestBody CompleteSignupRequest request, HttpServletRequest http) {
        rateLimiter.check("complete:" + http.getRemoteAddr(), 10, Duration.ofMinutes(15));
        return signupService.complete(request);
    }
}
