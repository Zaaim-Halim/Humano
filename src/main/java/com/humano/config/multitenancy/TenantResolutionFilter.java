package com.humano.config.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that resolves the current tenant from the incoming request and sets it on
 * {@link TenantContext} for the duration of the request.
 * <p>
 * Wired explicitly via {@code SecurityConfiguration#filterChain} ahead of
 * {@code UsernamePasswordAuthenticationFilter}; auto-registration as a top-level servlet
 * filter is suppressed by the {@code FilterRegistrationBean} in {@code SecurityConfiguration}
 * so the filter runs exactly once and ordering relative to Spring Security is explicit.
 *
 * @author Humano Team
 */
@Component
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TenantResolutionFilter.class);
    private static final String PLATFORM_PREFIX = "/api/platform/";

    private final TenantResolver tenantResolver;
    private final MultiTenantProperties properties;

    public TenantResolutionFilter(TenantResolver tenantResolver, MultiTenantProperties properties) {
        this.tenantResolver = tenantResolver;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isPlatform = path.startsWith(PLATFORM_PREFIX);
        try {
            // /api/platform/** runs under the configured platform tenant so Spring
            // Security's UserDetailsService loads admins from a real tenant DB (there is no
            // app_user table in master at v1). Regular requests follow header/subdomain
            // resolution as before.
            String tenantId = isPlatform ? properties.getPlatformTenant() : tenantResolver.resolveTenant(request);
            TenantContext.setCurrentTenant(tenantId);
            // Every log line for the rest of this request carries [tenant=<subdomain>].
            // The matching TaskDecorator in AsyncConfiguration propagates this to @Async workers.
            MDC.put(TenantContext.MDC_TENANT_KEY, tenantId);

            if (LOG.isDebugEnabled()) {
                LOG.debug(
                    "Resolved tenant '{}' for request: {} {} ({})",
                    tenantId,
                    request.getMethod(),
                    path,
                    isPlatform ? "platform-forced" : "request-resolved"
                );
            }

            // Business endpoints (anything under /api/** except onboarding/public, which
            // shouldNotFilter excludes) require a real tenant. /api/platform/** is now
            // forced to the configured platform tenant above, so this only triggers on
            // regular /api/** paths.
            if (TenantContext.MASTER.equals(tenantId) && path.startsWith("/api/")) {
                writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Missing tenant: provide X-Tenant-ID header or use a tenant subdomain"
                );
                return;
            }

            // Pin sessions to the tenant they authenticated against. If a previously
            // authenticated session is being reused with a different request tenant
            // (cookie replay across subdomains / X-Tenant-ID switch), reject with 401.
            HttpSession existing = request.getSession(false);
            if (existing != null) {
                Object sessionTenant = existing.getAttribute(TenantContext.SESSION_TENANT_ATTRIBUTE);
                if (sessionTenant != null && !sessionTenant.equals(tenantId)) {
                    LOG.warn("Session/tenant mismatch: session={} request={} uri={}", sessionTenant, tenantId, request.getRequestURI());
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Session is bound to a different tenant");
                    return;
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(TenantContext.MDC_TENANT_KEY);
        }
    }

    /**
     * Write a terminal error directly to the response (status + plain-text body) instead of
     * {@link HttpServletResponse#sendError}. {@code sendError} triggers an internal error
     * dispatch which re-enters Spring Security's chain and clobbers our status with a 401.
     */
    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
        response.flushBuffer();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // /api/platform/** is intentionally NOT excluded — doFilterInternal forces
        // the tenant context to the configured platform tenant so the UserDetailsService
        // can resolve the admin principal from that tenant's DB.
        return (
            path.startsWith("/management/") ||
            path.startsWith("/api/tenant-registration") ||
            path.startsWith("/api/public/") ||
            path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/content/") ||
            path.startsWith("/i18n/") ||
            path.equals("/") ||
            path.equals("/index.html") ||
            path.endsWith(".js") ||
            path.endsWith(".css") ||
            path.endsWith(".ico") ||
            path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".svg")
        );
    }
}
