package com.humano.dto.me.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Current-user profile update. Posted by an authenticated principal to
 * {@code POST /api/account} — limited to non-privileged fields. Role
 * changes, login changes, and activation flips are admin-only and live on
 * {@code dto.admin.requests.UpdateUserRequest} instead.
 */
public record UpdateMeRequest(
    @Size(max = 50) String firstName,
    @Size(max = 50) String lastName,
    @Email @NotBlank @Size(min = 5, max = 254) String email,
    @Size(min = 2, max = 10) String langKey,
    @Size(max = 256) String imageUrl
) {}
