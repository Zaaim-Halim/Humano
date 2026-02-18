package com.humano.config.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that resolves the current tenant from the incoming request
 * and sets it in the TenantContext for the duration of the request.
 *
 * @author Humano Team
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
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

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
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
