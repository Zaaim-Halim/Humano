package com.humano.security;

import com.humano.config.multitenancy.TenantContext;
import com.humano.domain.shared.Authority;
import com.humano.domain.shared.Permission;
import com.humano.repository.shared.AuthorityRepository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves whether an authority grants a given permission, backed by an in-memory cache.
 * <p>
 * Humano is <strong>database-per-tenant</strong>: every tenant DB has its own
 * {@code authority} / {@code permission} / {@code authority_permissions} rows (seeded from
 * {@link DefaultRolePermissions} / {@link PlatformRolePermissions} by
 * {@code TenantInitializationService}), so the same authority name (e.g. {@code ROLE_ADMIN})
 * can map to different permission sets in different tenants. The cache is therefore keyed by
 * tenant ({@link TenantContext}) — a single global map would let one tenant's mapping answer
 * authorization checks for another (cross-tenant bleed).
 * <p>
 * Each tenant's map is loaded lazily on first access and cached as an immutable snapshot.
 * After mutating a tenant's authorities/permissions, call {@link #evictCurrentTenant()} (or
 * {@link #refreshPermissionCache()}) so the next read reflects the change. The cache is safe
 * for concurrent access and resolves correctly on {@code @Async} workers, since
 * {@code TenantAwareTaskDecorator} propagates {@link TenantContext}.
 * <p>
 * This service does not seed — seeding is owned solely by {@code TenantInitializationService},
 * anchored to the same constants the {@code @RequirePermission} gates check.
 */
@Service
public class AuthorityPermissionService {

    private final Logger log = LoggerFactory.getLogger(AuthorityPermissionService.class);

    private final AuthorityRepository authorityRepository;

    /**
     * Per-tenant cache: {@code tenantId -> (authorityName -> {permissionName})}. Inner maps
     * and sets are immutable snapshots, so reads never need synchronization.
     */
    private final Map<String, Map<String, Set<String>>> cache = new ConcurrentHashMap<>();

    public AuthorityPermissionService(AuthorityRepository authorityRepository) {
        this.authorityRepository = authorityRepository;
    }

    // ==================== cache resolution ====================

    /**
     * Cache key for the calling thread's tenant. Requests with no tenant — or the master
     * context — collapse to {@link TenantContext#MASTER}; business {@code /api/**} requests
     * are rejected for master upstream, so that bucket never serves real business
     * permissions. Platform requests already carry the platform tenant key.
     */
    private String currentTenantKey() {
        String tenant = TenantContext.getCurrentTenant();
        return (tenant == null || TenantContext.MASTER.equals(tenant)) ? TenantContext.MASTER : tenant;
    }

    /** Lazily resolve the calling tenant's authority→permission map. */
    private Map<String, Set<String>> tenantMap() {
        return cache.computeIfAbsent(currentTenantKey(), key -> loadForCurrentTenant());
    }

    /**
     * Build an immutable authority→permission snapshot for the current tenant DB. Uses a
     * fetch-join query so the permission collections are materialized in one round-trip and
     * no open session is required when this runs from the {@code computeIfAbsent} lambda.
     */
    private Map<String, Set<String>> loadForCurrentTenant() {
        Map<String, Set<String>> map = new HashMap<>();
        for (Authority authority : authorityRepository.findAllWithPermissions()) {
            Set<String> permissionNames = authority
                .getPermissions()
                .stream()
                .map(Permission::getName)
                .collect(Collectors.toUnmodifiableSet());
            map.put(authority.getName(), permissionNames);
        }
        log.debug("Loaded permission cache for tenant '{}' with {} authorities", currentTenantKey(), map.size());
        return Map.copyOf(map);
    }

    /**
     * Evict the calling tenant's cached mapping; the next read reloads it. Call this after
     * mutating authorities or permissions in the current tenant.
     */
    public void evictCurrentTenant() {
        cache.remove(currentTenantKey());
    }

    /**
     * Rebuild the calling tenant's cached mapping immediately (evict + reload), so the new
     * mapping is visible without waiting for the next read.
     */
    @Transactional(readOnly = true)
    public void refreshPermissionCache() {
        cache.put(currentTenantKey(), loadForCurrentTenant());
    }

    /** Drop every tenant's cache (e.g. for tests). */
    public void clearAllCaches() {
        cache.clear();
    }

    // ==================== queries ====================

    /**
     * Check if an authority grants a permission <em>in the calling tenant</em>.
     *
     * @param authority the authority name to check
     * @param permission the permission name to check for
     * @return true if the authority grants the permission in the current tenant
     */
    public boolean hasPermission(String authority, String permission) {
        Set<String> permissions = tenantMap().get(authority);
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Get all permissions granted by an authority in the calling tenant.
     *
     * @param authority the authority name
     * @return an unmodifiable set of permission names, empty if the authority is unknown
     */
    public Set<String> getPermissionsForAuthority(String authority) {
        return tenantMap().getOrDefault(authority, Set.of());
    }

    /**
     * Get the union of permissions granted by a set of authorities in the calling tenant.
     * Convenience for resolving a user's effective permissions from their roles.
     *
     * @param authorities the authority names
     * @return an unmodifiable set of effective permission names
     */
    public Set<String> getPermissionsForAuthorities(Collection<String> authorities) {
        Map<String, Set<String>> map = tenantMap();
        Set<String> effective = new HashSet<>();
        for (String authority : authorities) {
            Set<String> perms = map.get(authority);
            if (perms != null) {
                effective.addAll(perms);
            }
        }
        return Collections.unmodifiableSet(effective);
    }

    /**
     * Get all authorities that grant a permission in the calling tenant.
     *
     * @param permission the permission name
     * @return a set of authority names that grant the permission
     */
    public Set<String> getAuthoritiesWithPermission(String permission) {
        Set<String> authorities = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : tenantMap().entrySet()) {
            if (entry.getValue().contains(permission)) {
                authorities.add(entry.getKey());
            }
        }
        return authorities;
    }
}
