package com.humano.dto.admin.responses;

import com.humano.domain.shared.User;
import java.util.UUID;

/**
 * Minimal public view of a user — id + login only. Returned from
 * {@code GET /api/users}, reachable by any authenticated user inside the
 * tenant. Use this whenever a feature needs to render an actor identifier
 * without leaking email, name, or activation state.
 */
public record PublicUserResponse(UUID id, String login) {
    public static PublicUserResponse fromUser(User user) {
        return new PublicUserResponse(user.getId(), user.getLogin());
    }
}
