package com.humano.dto.signup;

import com.humano.domain.enumeration.CountryCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * The public "Start free trial" form. Nothing is stored: these details travel in the signed link
 * emailed to {@code email}. The subdomain is capped at 25 characters because the workspace's
 * database user is {@code tenant_<subdomain>} and MySQL user names stop at 32. {@code website} is a
 * honeypot: hidden from people, so a filled-in value means a bot. {@code payrollTemplateId} is the
 * statutory payroll template the workspace starts with, if the visitor chose one (optional).
 * {@code acceptedTerms} must be ticked, and {@code termsVersion} is the version of the Terms and Data
 * Processing Addendum the form showed ({@link com.humano.config.Constants#TERMS_VERSION}).
 */
public record StartSignupRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 2, max = 100) String companyName,
    @NotBlank
    @Size(min = 3, max = 25)
    @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$", message = "Lowercase letters, numbers and hyphens only")
    String subdomain,
    @NotNull CountryCode country,
    @Size(max = 64) String timezone,
    @NotNull UUID planId,
    @Size(max = 100) String payrollTemplateId,
    @NotNull @AssertTrue Boolean acceptedTerms,
    @NotBlank @Size(max = 20) String termsVersion,
    @Size(max = 200) String website
) {}
