package com.humano.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Source of truth for the <strong>platform (SaaS owner)</strong> role → permission mapping.
 * <p>
 * These roles and permissions are seeded <strong>only</strong> into the platform tenant DB
 * (the tenant designated by {@code MultiTenantProperties#getPlatformTenant()}), never into a
 * business tenant. This keeps {@code /api/platform/**} reachable only by principals that live
 * in the platform realm, so a business tenant's {@link AuthoritiesConstants#ADMIN} can never
 * acquire platform authority.
 *
 * @see DefaultRolePermissions for the per-business-tenant mapping
 */
public final class PlatformRolePermissions {

    private static final Map<String, Set<String>> ROLE_TO_PERMISSIONS;
    private static final Set<String> ALL_PERMISSIONS;

    static {
        Set<String> ownerPerms = new LinkedHashSet<>(
            Set.of(
                PermissionsConstants.PROVISION_TENANT,
                PermissionsConstants.SUSPEND_TENANT,
                PermissionsConstants.DEPROVISION_TENANT,
                PermissionsConstants.VIEW_PLATFORM_TENANTS,
                PermissionsConstants.VIEW_PLATFORM_BILLING,
                PermissionsConstants.MANAGE_PLATFORM_BILLING,
                PermissionsConstants.IMPERSONATE_TENANT,
                PermissionsConstants.VIEW_PLATFORM_METRICS
            )
        );

        // Platform admin: everything except the destructive / owner-only actions.
        Set<String> adminPerms = new LinkedHashSet<>(
            Set.of(
                PermissionsConstants.PROVISION_TENANT,
                PermissionsConstants.SUSPEND_TENANT,
                PermissionsConstants.VIEW_PLATFORM_TENANTS,
                PermissionsConstants.VIEW_PLATFORM_BILLING,
                PermissionsConstants.VIEW_PLATFORM_METRICS
            )
        );

        Map<String, Set<String>> map = new LinkedHashMap<>();
        map.put(AuthoritiesConstants.PLATFORM_OWNER, Collections.unmodifiableSet(ownerPerms));
        map.put(AuthoritiesConstants.PLATFORM_ADMIN, Collections.unmodifiableSet(adminPerms));
        ROLE_TO_PERMISSIONS = Collections.unmodifiableMap(map);
        ALL_PERMISSIONS = Collections.unmodifiableSet(new LinkedHashSet<>(ownerPerms));
    }

    private PlatformRolePermissions() {}

    /** @return the immutable platform role → permission mapping. */
    public static Map<String, Set<String>> rolePermissions() {
        return ROLE_TO_PERMISSIONS;
    }

    /** @return all platform role names. */
    public static Set<String> roles() {
        return ROLE_TO_PERMISSIONS.keySet();
    }

    /** @return all distinct platform permission names. */
    public static Set<String> permissions() {
        return ALL_PERMISSIONS;
    }
}
