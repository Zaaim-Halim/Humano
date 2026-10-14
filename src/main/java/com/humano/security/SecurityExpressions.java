package com.humano.security;

import java.util.Collection;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Utility class for Spring Security expressions in method-level security annotations.
 * Provides methods to check if the current user has a specific permission based on their roles.
 */
@Component("securityExpressions")
public class SecurityExpressions {

    private final AuthorityPermissionService authorityPermissionService;

    @Autowired
    public SecurityExpressions(AuthorityPermissionService authorityPermissionService) {
        this.authorityPermissionService = authorityPermissionService;
    }

    /**
     * Check if the current user has a specific permission based on their roles.
     *
     * @param permission the permission to check for
     * @return true if the user has the permission, false otherwise
     */
    /** True when the signed-in user is the given user / employee (they share ids). */
    public boolean isCurrentUser(UUID id) {
        return id != null && SecurityUtils.getCurrentUserId().map(id::equals).orElse(false);
    }

    public boolean hasPermission(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        for (GrantedAuthority authority : authorities) {
            if (authorityPermissionService.hasPermission(authority.getAuthority(), permission)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the current user has any of the specified permissions based on their roles.
     *
     * @param permissions the permissions to check for
     * @return true if the user has any of the permissions, false otherwise
     */
    public boolean hasAnyPermission(String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the current user has all of the specified permissions based on their roles.
     *
     * @param permissions the permissions to check for
     * @return true if the user has all of the permissions, false otherwise
     */
    public boolean hasAllPermissions(String... permissions) {
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }
}
