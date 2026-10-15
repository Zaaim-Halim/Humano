package com.humano.dto.demo;

import com.humano.domain.enumeration.CountryCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A demo workspace to set up. {@code planId} is the plan whose features it shows (default: the
 * featured plan); {@code demoRequestId} links it to the request it answers.
 */
public record CreateDemoWorkspaceRequest(
    @NotBlank @Size(min = 2, max = 100) String companyName,
    @NotBlank
    @Size(min = 3, max = 63)
    @Pattern(regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$", message = "Lowercase letters, numbers and hyphens only")
    String subdomain,
    @NotNull CountryCode country,
    @Size(max = 64) String timezone,
    @Size(max = 100) String contactName,
    @Email @Size(max = 254) String contactEmail,
    @Min(1) @Max(90) Integer durationDays,
    UUID planId,
    Boolean sampleData,
    UUID demoRequestId
) {}
