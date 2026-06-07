package com.humano.dto.auth.requests;

import com.humano.config.Constants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public self-registration request. Posted to {@code POST /api/register} by
 * anonymous callers; the password is plaintext over TLS and hashed at the
 * service layer. {@link #langKey} is optional — falls back to
 * {@link Constants#DEFAULT_LANGUAGE} when blank.
 */
public record RegisterUserRequest(
    @NotBlank @Pattern(regexp = Constants.LOGIN_REGEX) @Size(min = 1, max = 50) String login,
    @Size(max = 50) String firstName,
    @Size(max = 50) String lastName,
    @Email @NotBlank @Size(min = 5, max = 254) String email,
    @Size(min = Constants.PASSWORD_MIN_LENGTH, max = Constants.PASSWORD_MAX_LENGTH) String password,
    @Size(min = 2, max = 10) String langKey
) {}
