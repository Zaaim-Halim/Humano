package com.humano.service.demo;

import com.humano.domain.tenant.DemoRequest;
import com.humano.repository.tenant.DemoRequestRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes "Book a demo" requests that did not lead to a customer once nobody has touched them for
 * {@code humano.retention.demo-requests.months}, as the Privacy Policy says. A request whose demo
 * workspace was converted into a customer is kept: it is part of that customer's history.
 */
@Component
public class DemoRequestRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(DemoRequestRetentionJob.class);

    private final DemoRequestRepository demoRequestRepository;
    private final TransactionTemplate masterTx;
    private final int months;

    public DemoRequestRetentionJob(
        DemoRequestRepository demoRequestRepository,
        @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager,
        @Value("${humano.retention.demo-requests.months:24}") int months
    ) {
        this.demoRequestRepository = demoRequestRepository;
        this.masterTx = new TransactionTemplate(masterTransactionManager);
        this.months = months;
    }

    @Scheduled(cron = "0 55 3 * * *") // nightly, 03:55
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void purge() {
        Instant before = Instant.now().atZone(ZoneOffset.UTC).minusMonths(months).toInstant();
        Integer deleted = masterTx.execute(tx -> {
            List<DemoRequest> stale = demoRequestRepository
                .findUntouchedSince(before)
                .stream()
                .filter(DemoRequestRetentionJob::notACustomer)
                .toList();
            demoRequestRepository.deleteAll(stale);
            return stale.size();
        });
        if (deleted != null && deleted > 0) {
            log.info("Deleted {} demo request(s) untouched for {} months", deleted, months);
        }
    }

    /** No workspace, or one that is still (or was deleted as) a demo — never converted into a customer. */
    static boolean notACustomer(DemoRequest request) {
        return request.getTenant() == null || request.getTenant().isDemo();
    }
}
