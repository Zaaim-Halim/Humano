package com.humano.service.multitenancy;

/**
 * Exception thrown when tenant provisioning fails.
 *
 * @author Humano Team
 */
public class TenantProvisioningException extends RuntimeException {

    public TenantProvisioningException(String message) {
        super(message);
    }

    public TenantProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
