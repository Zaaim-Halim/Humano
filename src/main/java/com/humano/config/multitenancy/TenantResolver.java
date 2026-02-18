package com.humano.config.multitenancy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the tenant identifier from incoming requests.
 * Supports multiple resolution strategies in order of priority:
 * 1. X-Tenant-ID header
 * 2. Subdomain extraction
 * 3. JWT token claim
 *
 * @author Humano Team
 */
@Component
public class TenantResolver {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    /**
     * Resolves the tenant identifier from the given request.
     *
     * @param request the HTTP request
     * @return the tenant identifier, or "master" if not found
     */
    public String resolveTenant(HttpServletRequest request) {
        // Strategy 1: Check header (useful for API clients)
        String tenantFromHeader = request.getHeader(TENANT_HEADER);
        if (tenantFromHeader != null && !tenantFromHeader.isBlank()) {
            return tenantFromHeader.trim().toLowerCase();
        }

        // Strategy 2: Extract from subdomain
        String host = request.getServerName();
        String tenantFromSubdomain = extractSubdomain(host);
        if (tenantFromSubdomain != null) {
            return tenantFromSubdomain;
        }

        // Strategy 3: Extract from JWT (handled by security filter)
        // Falls back to master database for platform-level operations
        return "master";
    }

    /**
     * Extracts the subdomain from the host.
     * Expected format: {tenant}.humano.com or {tenant}.localhost
     *
     * @param host the server hostname
     * @return the tenant subdomain, or null if not found
     */
    private String extractSubdomain(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }

        // Handle localhost case
        if ("localhost".equals(host) || host.startsWith("127.0.0.1")) {
            return null;
        }

        String[] parts = host.split("\\.");
        if (parts.length >= 2) {
            String potentialTenant = parts[0].toLowerCase();
            // Skip common prefixes that are not tenant identifiers
            if (
                !"www".equals(potentialTenant) &&
                !"api".equals(potentialTenant) &&
                !"app".equals(potentialTenant) &&
                !"admin".equals(potentialTenant)
            ) {
                return potentialTenant;
            }
        }
        return null;
    }
}
