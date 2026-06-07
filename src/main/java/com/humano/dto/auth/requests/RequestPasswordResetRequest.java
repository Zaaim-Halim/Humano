package com.humano.dto.auth.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public reset-init request. Posted to
 * {@code POST /api/account/reset-password/init}. The service responds
 * identically whether or not the email matches a real account, to avoid
 * leaking which emails are registered.
 */
public record RequestPasswordResetRequest(@Email @NotBlank @Size(min = 5, max = 254) String email) {}
