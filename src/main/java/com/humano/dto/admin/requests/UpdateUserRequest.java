package com.humano.dto.admin.requests;

import com.humano.config.Constants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * Admin-initiated user update. Posted to {@code PUT /api/admin/users} by
 * callers holding {@code ROLE_ADMIN}. {@link #authorities} is the full
 * replacement set — anything missing is removed.
 */
public record UpdateUserRequest(
    @NotNull UUID id,
    @NotBlank @Pattern(regexp = Constants.LOGIN_REGEX) @Size(min = 1, max = 50) String login,
    @Size(max = 50) String firstName,
    @Size(max = 50) String lastName,
    @Email @NotBlank @Size(min = 5, max = 254) String email,
    @Size(max = 256) String imageUrl,
    boolean activated,
    @Size(min = 2, max = 10) String langKey,
    Set<String> authorities
) {}
