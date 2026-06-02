package com.humano.config.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final TenantResolver tenantResolver;

    public TenantResolutionFilter(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            String tenantId = tenantResolver.resolveTenant(request);
            TenantContext.setCurrentTenant(tenantId);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Resolved tenant '{}' for request: {} {}", tenantId, request.getMethod(), request.getRequestURI());
            }

            // Business endpoints (anything under /api/** except platform/onboarding/public,
            // which shouldNotFilter already excludes) require a real tenant. Reject the
            // reserved "master" sentinel with 400.
            if (TenantContext.MASTER.equals(tenantId) && request.getRequestURI().startsWith("/api/")) {
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
        // Skip tenant resolution for platform admin endpoints and public resources
        return (
            path.startsWith("/api/platform/") ||
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
