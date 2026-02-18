package com.humano.config.multitenancy;

/**
 * Exception thrown when a tenant is not found or cannot be resolved.
 *
 * @author Humano Team
 */
public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(String message) {
        super(message);
    }

    public TenantNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
