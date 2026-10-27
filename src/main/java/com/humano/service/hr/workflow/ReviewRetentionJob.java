package com.humano.service.hr.workflow;

import com.humano.dto.hr.workflow.responses.ReviewRetentionPurgeResult;
import com.humano.tenancy.TenantIteration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes performance review records past their retention period: in every active tenant that turned
 * automatic deletion on, the review cycles due (see {@link ReviewRetentionService#purgeDue()}). Runs
 * nightly; a cycle that fails is left whole and tried again the next night.
 */
@Component
public class ReviewRetentionJob {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewRetentionJob.class);

    private final TenantIteration tenantIteration;
    private final ReviewRetentionService retentionService;

    public ReviewRetentionJob(TenantIteration tenantIteration, ReviewRetentionService retentionService) {
        this.tenantIteration = tenantIteration;
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "0 40 3 * * *") // nightly, 03:40
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void purgeDue() {
        tenantIteration.forEachActiveTenant(subdomain -> {
            ReviewRetentionPurgeResult result = retentionService.purgeDue();
            if (result.cycles() > 0 || result.failed() > 0) {
                LOG.info(
                    "Tenant '{}': {} review cycle(s) with {} review(s) deleted under the retention period, {} failed",
                    subdomain,
                    result.cycles(),
                    result.reviews(),
                    result.failed()
                );
            }
        });
    }
}
