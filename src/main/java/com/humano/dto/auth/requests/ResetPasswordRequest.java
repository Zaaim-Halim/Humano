package com.humano.dto.auth.requests;

import com.humano.config.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public reset-finish request. Posted to
 * {@code POST /api/account/reset-password/finish} with the one-shot reset
 * key emailed to the user plus the new password.
 */
public record ResetPasswordRequest(
    @NotBlank String key,
    @Size(min = Constants.PASSWORD_MIN_LENGTH, max = Constants.PASSWORD_MAX_LENGTH) String newPassword
) {}
