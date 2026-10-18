package com.humano.service.billing.entitlement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.repository.hr.EmployeeDocumentRepository;
import com.humano.repository.storage.StoredFileRepository;
import com.humano.web.rest.errors.PlanLimitException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Storage is counted from the sizes recorded in the workspace's database, whatever the provider. */
class StorageQuotaServiceTest {

    private static final long GB = 1024L * 1024 * 1024;

    private final StoredFileRepository files = mock(StoredFileRepository.class);
    private final EmployeeDocumentRepository documents = mock(EmployeeDocumentRepository.class);
    private final EntitlementService entitlements = mock(EntitlementService.class);
    private final StorageQuotaService quota = new StorageQuotaService(files, documents, entitlements);

    /** A 10 GB plan, with the real check. */
    private final EntitlementService tenGbPlan = new EntitlementService(
        null,
        null,
        null,
        null,
        new com.humano.config.multitenancy.MultiTenantProperties(),
        mock(org.springframework.transaction.PlatformTransactionManager.class)
    ) {
        @Override
        public Entitlements current() {
            return new Entitlements(
                UUID.randomUUID(),
                "Starter",
                Map.of(FeatureCodes.STORAGE, new Entitlements.Label(UUID.randomUUID(), "Document storage", "GB")),
                Map.of(FeatureCodes.STORAGE, new Entitlements.Grant(true, 10))
            );
        }
    };

    @BeforeEach
    void usage() {
        when(files.totalSizeBytes()).thenReturn(3 * GB);
        when(documents.totalSizeBytes()).thenReturn(6 * GB);
    }

    @Test
    void addsUpEveryKindOfUpload() {
        org.assertj.core.api.Assertions.assertThat(quota.usedBytes()).isEqualTo(9 * GB);
    }

    @Test
    void uploadThatFitsIsAccepted() {
        StorageQuotaService q = new StorageQuotaService(files, documents, tenGbPlan);
        assertThatCode(() -> q.checkFits(GB / 2)).doesNotThrowAnyException();
    }

    @Test
    void uploadPastTheLimitIsRefused() {
        StorageQuotaService q = new StorageQuotaService(files, documents, tenGbPlan);
        assertThatThrownBy(() -> q.checkFits(2 * GB)).isInstanceOf(PlanLimitException.class);
    }

    @Test
    void smallerReplacementIsNeverRefused() {
        quota.checkFits(-GB);
        quota.checkFits(0);
        verify(entitlements, never()).checkWithin(anyString(), anyDouble());
    }
}
