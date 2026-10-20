package com.humano.service.hr.workflow;

import com.humano.tenancy.TenantIteration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends the employment of leavers once their last working day is over: in every active tenant,
 * employees still OFFBOARDING past their last day become TERMINATED (see
 * {@link EmployeeLifecycleWorkflowService#terminateDueOffboardings()}). Runs hourly so a restart
 * or a missed run is caught up the same day; the work is idempotent.
 */
@Component
public class OffboardingTerminationJob {

    private static final Logger LOG = LoggerFactory.getLogger(OffboardingTerminationJob.class);

    private final TenantIteration tenantIteration;
    private final EmployeeLifecycleWorkflowService lifecycleService;

    public OffboardingTerminationJob(TenantIteration tenantIteration, EmployeeLifecycleWorkflowService lifecycleService) {
        this.tenantIteration = tenantIteration;
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(cron = "0 15 * * * *") // hourly, at a quarter past
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void terminateLeavers() {
        tenantIteration.forEachActiveTenant(subdomain -> {
            int terminated = lifecycleService.terminateDueOffboardings();
            if (terminated > 0) {
                LOG.info("Tenant '{}': {} leaver(s) terminated after their last working day", subdomain, terminated);
            }
        });
    }
}
