package com.humano.dto.me.requests;

import com.humano.config.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Current-user password change. Posted by an authenticated principal to
 * {@code POST /api/account/change-password}; the service verifies
 * {@link #currentPassword} against the stored hash before accepting
 * {@link #newPassword}.
 */
public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @Size(min = Constants.PASSWORD_MIN_LENGTH, max = Constants.PASSWORD_MAX_LENGTH) String newPassword
) {}
