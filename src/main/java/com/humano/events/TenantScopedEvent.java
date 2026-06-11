package com.humano.events;

/**
 * Contract every domain event in this multi-tenant app must satisfy: carry the
 * originating tenant's subdomain so listeners can route to the correct tenant
 * datasource regardless of which thread they run on.
 *
 * <p>The publisher reads {@code TenantContext.getCurrentTenant()} (or
 * {@code tenant.getSubdomain()} when operating on the master DB) at publish
 * time and stamps it onto the event. Listeners that touch tenant-scoped data
 * MUST set {@code TenantContext.setCurrentTenant(event.tenantSubdomain())}
 * before any tenant repository call and restore the previous value in a
 * {@code finally} block — relying on the publisher's thread-local is brittle
 * across {@code @Async} hops, {@code AFTER_COMMIT} phase boundaries, or event
 * replay.
 *
 * <p>Master-DB-only listeners (e.g. mailing the tenant admin) can ignore the
 * field, but every event still carries it so a future listener that needs
 * tenant routing can be added without changing the event contract.
 *
 * @see com.humano.config.multitenancy.TenantContext
 */
public interface TenantScopedEvent {
    /**
     * @return the originating tenant's subdomain, or {@code null} if the event
     *         was published from a non-tenant code path (in which case
     *         tenant-routing listeners should bail and log a warning).
     */
    String tenantSubdomain();
}
