package com.humano.dto.admin.requests;

import com.humano.config.Constants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Admin-initiated user creation. Posted to
 * {@code POST /api/admin/users} by callers holding {@code ROLE_ADMIN}.
 * An activation email is sent on success; the recipient sets their
 * password through the reset-password flow.
 */
public record CreateUserRequest(
    @NotBlank @Pattern(regexp = Constants.LOGIN_REGEX) @Size(min = 1, max = 50) String login,
    @Size(max = 50) String firstName,
    @Size(max = 50) String lastName,
    @Email @NotBlank @Size(min = 5, max = 254) String email,
    @Size(max = 256) String imageUrl,
    @Size(min = 2, max = 10) String langKey,
    Set<String> authorities
) {}
