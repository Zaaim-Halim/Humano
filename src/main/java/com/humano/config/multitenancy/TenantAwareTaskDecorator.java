package com.humano.config.multitenancy;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Propagates {@link TenantContext} + the SLF4J MDC across the boundary from the request
 * thread to a {@code @Async} worker thread. Without this, tenant-scoped repositories
 * invoked from an async listener would lose their context and hit the master DB (or fail).
 */
public class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String submitterTenant = TenantContext.getCurrentTenant();
        Map<String, String> submitterMdc = MDC.getCopyOfContextMap();
        return () -> {
            String previousTenant = TenantContext.getCurrentTenant();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            try {
                if (submitterTenant != null) {
                    TenantContext.setCurrentTenant(submitterTenant);
                }
                if (submitterMdc != null) {
                    MDC.setContextMap(submitterMdc);
                } else {
                    MDC.clear();
                }
                runnable.run();
            } finally {
                // Restore whatever the worker thread had before — usually nothing, but
                // pooled threads from prior tasks may still hold state.
                if (previousTenant != null) {
                    TenantContext.setCurrentTenant(previousTenant);
                } else {
                    TenantContext.clear();
                }
                if (previousMdc != null) {
                    MDC.setContextMap(previousMdc);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
