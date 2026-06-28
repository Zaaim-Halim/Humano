package com.humano.dto.hr.requests;

import com.humano.config.Constants;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.dto.hr.responses.CountryRef;
import com.humano.dto.hr.responses.ReferenceDataRef;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * DTO record for provisioning a new employee in a single step.
 * <p>
 * Combines the identity/auth fields needed to create the backing {@code User}
 * account (login, name, email, language, authorities) with the HR profile
 * fields. The underlying user account is created and an activation email is
 * sent so the recipient can set their own password — callers never manage
 * users directly.
 */
public record CreateEmployeeRequest(
    // --- account / identity ---
    @NotBlank @Pattern(regexp = Constants.LOGIN_REGEX) @Size(min = 1, max = 50) String login,
    @Size(max = 50) String firstName,
    @Size(max = 50) String lastName,
    @Email @NotBlank @Size(min = 5, max = 254) String email,
    @Size(max = 256) String imageUrl,
    @Size(min = 2, max = 10) String langKey,
    Set<String> authorities,
    // --- HR profile ---
    String jobTitle,
    String phone,
    @NotNull(message = "Start date is required") LocalDate startDate,
    LocalDate endDate,
    EmployeeStatus status,
    UUID countryId,
    UUID departmentId,
    @NotNull(message = "Position is required") UUID positionId,
    @NotNull(message = "Organizational unit is required") UUID unitId,
    UUID managerId,
    // --- reference-data relationships (nested; only id is read) ---
    CountryRef nationality,
    ReferenceDataRef maritalStatus,
    ReferenceDataRef employmentType,
    ReferenceDataRef grade,
    ReferenceDataRef level,
    ReferenceDataRef category,
    ReferenceDataRef terminationReason
) {
    /** Project the account/identity fields onto the admin {@code CreateUserRequest}. */
    public com.humano.dto.admin.requests.CreateUserRequest toCreateUserRequest() {
        return new com.humano.dto.admin.requests.CreateUserRequest(login, firstName, lastName, email, imageUrl, langKey, authorities);
    }

    /** Project the HR profile fields onto the profile-only request. */
    public CreateEmployeeProfileRequest toProfileRequest() {
        return new CreateEmployeeProfileRequest(
            jobTitle,
            phone,
            startDate,
            endDate,
            status,
            countryId,
            departmentId,
            positionId,
            unitId,
            managerId,
            nationality,
            maritalStatus,
            employmentType,
            grade,
            level,
            category,
            terminationReason
        );
    }
}
