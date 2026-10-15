package com.humano.domain.enumeration.tenant;

/**
 * Where a demo workspace's setup stands: provisioning the workspace, filling it with the sample
 * company, done, or failed (retryable — both steps are resumable).
 */
public enum DemoSetupStatus {
    PROVISIONING,
    SEEDING,
    READY,
    FAILED,
}
