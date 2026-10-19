package com.humano.config.timezone;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.IntegrationTest;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import com.humano.domain.enumeration.hr.WorkflowType;
import com.humano.domain.hr.WorkflowInstance;
import com.humano.repository.hr.workflow.WorkflowInstanceRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Date-times are stored in UTC ({@code hibernate.jdbc.time_zone}) whatever the server's zone, in
 * both the master and the tenant persistence units — so a stored instant reads back as the same
 * wall-clock time in UTC.
 */
@IntegrationTest
class HibernateTimeZoneIT {

    @Autowired
    @Qualifier("tenantEntityManagerFactory")
    private EntityManagerFactory tenantEntityManagerFactory;

    @Autowired
    @Qualifier("masterEntityManagerFactory")
    private EntityManagerFactory masterEntityManagerFactory;

    @Autowired
    @Qualifier("tenantDataSource")
    private DataSource tenantDataSource;

    @Autowired
    private WorkflowInstanceRepository workflowInstanceRepository;

    @Test
    void bothPersistenceUnitsUseUtc() {
        assertThat(tenantEntityManagerFactory.getProperties()).containsEntry("hibernate.jdbc.time_zone", "UTC");
        assertThat(masterEntityManagerFactory.getProperties()).containsEntry("hibernate.jdbc.time_zone", "UTC");
    }

    @Test
    void anInstantIsStoredAsUtcWallClockTime() {
        String marker = "TimeZoneIT-" + UUID.randomUUID();
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setWorkflowType(WorkflowType.ONBOARDING);
        workflow.setEntityId(UUID.randomUUID());
        workflow.setEntityType(marker);
        workflow.setStatus(WorkflowStatus.DRAFT);
        workflow.setDueDate(Instant.parse("2014-11-12T05:10:00Z"));
        WorkflowInstance saved = workflowInstanceRepository.saveAndFlush(workflow);
        try {
            // Read the raw column over plain JDBC (committed above), bypassing Hibernate's conversion.
            String stored = new JdbcTemplate(tenantDataSource).queryForObject(
                "SELECT due_date FROM workflow_instance WHERE entity_type = ?",
                String.class,
                marker
            );
            assertThat(stored).startsWith("2014-11-12 05:10:00");
        } finally {
            workflowInstanceRepository.delete(saved);
        }
    }
}
