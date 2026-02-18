package com.humano.config.multitenancy;

import java.lang.annotation.*;

/**
 * Marker annotation for entities that belong to tenant-specific databases.
 * Each tenant has their own isolated copy of these tables in their own database.
 *
 * @author Humano Team
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantDatabaseEntity {
}
