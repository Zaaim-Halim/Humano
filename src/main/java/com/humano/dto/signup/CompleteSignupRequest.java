package com.humano.dto.signup;

import com.humano.config.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The confirmation page: the emailed link's token, and the admin's name and password. Starts the workspace setup. */
public record CompleteSignupRequest(
    @NotBlank @Size(max = 2000) String token,
    @NotBlank @Size(max = 50) String firstName,
    @NotBlank @Size(max = 50) String lastName,
    @NotBlank @Size(min = Constants.PASSWORD_MIN_LENGTH, max = Constants.PASSWORD_MAX_LENGTH) String password
) {}
