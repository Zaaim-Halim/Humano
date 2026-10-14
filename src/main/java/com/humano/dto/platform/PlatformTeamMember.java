package com.humano.dto.platform;

import com.humano.domain.shared.User;
import java.time.Instant;
import java.util.UUID;

/**
 * A member of the platform (SaaS owner) team, with their platform role. {@code invitationPending}:
 * invited but has not set a password yet.
 */
public record PlatformTeamMember(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String imageUrl,
    boolean activated,
    boolean invitationPending,
    String role,
    Instant createdDate
) {
    public static PlatformTeamMember from(User user, String role) {
        return new PlatformTeamMember(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getImageUrl(),
            user.isActivated(),
            user.getResetKey() != null,
            role,
            user.getCreatedDate()
        );
    }
}
