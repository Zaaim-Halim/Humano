package com.humano.config.multitenancy;

import java.lang.annotation.*;

/**
 * Marker annotation for entities that belong to the master database.
 * These entities are shared across all tenants (billing, subscriptions, etc.)
 *
 * @author Humano Team
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MasterDatabaseEntity {
}
