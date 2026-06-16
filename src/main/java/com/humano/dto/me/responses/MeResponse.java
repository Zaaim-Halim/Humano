package com.humano.dto.me.responses;

import com.humano.domain.shared.Authority;
import com.humano.domain.shared.User;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Returned from {@code GET /api/account} — the current user's view of their
 * own record. Includes both authorities (roles) and the effective permissions
 * derived from those roles, so the SPA can gate UI on fine-grained permissions
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
    Set<String> authorities,
    Set<String> permissions
) {
    /**
     * Build the response from the user plus the effective permissions resolved
     * for that user's authorities in the current tenant (see
     * {@code AuthorityPermissionService#getPermissionsForAuthorities}).
     */
    public static MeResponse fromUser(User user, Set<String> permissions) {
        return new MeResponse(
            user.getId(),
            user.getLogin(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getImageUrl(),
            user.isActivated(),
            user.getLangKey(),
            user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toUnmodifiableSet()),
            permissions
        );
    }
}
