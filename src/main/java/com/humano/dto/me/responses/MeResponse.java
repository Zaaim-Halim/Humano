package com.humano.dto.me.responses;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Returned from {@code GET /api/account} — the current user's view of their
 * own record. Includes authorities (read-only here) so the SPA can adapt UI
 * without a follow-up call.
 */
public record MeResponse(
    UUID id,
    String login,
    String firstName,
    String lastName,
    String email,
    String imageUrl,
    boolean activated,
    String langKey,
    Set<String> authorities
) {
    public static MeResponse fromUser(User user) {
        return new MeResponse(
            user.getId(),
            user.getLogin(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getImageUrl(),
            user.isActivated(),
            user.getLangKey(),
            user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toUnmodifiableSet())
        );
    }
}
