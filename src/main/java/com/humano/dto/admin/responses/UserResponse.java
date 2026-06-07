package com.humano.dto.admin.responses;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Full admin view of a user, including auditing metadata. Returned from
 * {@code GET /api/admin/users}, {@code GET /api/admin/users/{login}},
 * {@code POST /api/admin/users}, {@code PUT /api/admin/users}.
 */
public record UserResponse(
    UUID id,
    String login,
    String firstName,
    String lastName,
    String email,
    String imageUrl,
    boolean activated,
    String langKey,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate,
    Set<String> authorities
) {
    public static UserResponse fromUser(User user) {
        return new UserResponse(
            user.getId(),
            user.getLogin(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getImageUrl(),
            user.isActivated(),
            user.getLangKey(),
            user.getCreatedBy(),
            user.getCreatedDate(),
            user.getLastModifiedBy(),
            user.getLastModifiedDate(),
            user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toUnmodifiableSet())
        );
    }
}
