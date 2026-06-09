package com.humano.repository.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.humano.IntegrationTest;
import com.humano.repository.shared.EmployeeRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Smoke tests that execute every hierarchy JPQL query against the real Hibernate
 * parser + the test database.
 * <p>
 * The point isn't business logic — it's catching {@code QuerySyntaxException},
 * unresolved attribute references, type mismatches in constructor expressions,
 * or dialect-specific function failures that {@code mvn compile} never sees.
 * Each query is invoked with parameters that match no rows, so the assertions
 * only check that the call completed.
 */
@IntegrationTest
@Transactional("tenantTransactionManager")
class HierarchyQueriesIT {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private OrganizationalUnitRepository organizationalUnitRepository;

    private static final String UNMATCHED_PATH = "/__hierarchy-it__/" + UUID.randomUUID();

    @Test
    void employeeSubtreeQueryParsesAndExecutes() {
        assertThatCode(() -> employeeRepository.findManagerSubtree(UNMATCHED_PATH)).doesNotThrowAnyException();
        assertThat(employeeRepository.findManagerSubtree(UNMATCHED_PATH)).isEmpty();
    }

    @Test
    void employeesInUnitSubtreeQueryParsesAndExecutes() {
        assertThatCode(() -> employeeRepository.findEmployeesInUnitSubtree(UNMATCHED_PATH)).doesNotThrowAnyException();
        assertThat(employeeRepository.findEmployeesInUnitSubtree(UNMATCHED_PATH)).isEmpty();
    }

    @Test
    void employeeHierarchyRowsByIdsQueryParsesAndExecutes() {
        List<UUID> ids = List.of(UUID.randomUUID());
        assertThatCode(() -> employeeRepository.findHierarchyRowsByIds(ids)).doesNotThrowAnyException();
        assertThat(employeeRepository.findHierarchyRowsByIds(ids)).isEmpty();
    }

    @Test
    void employeeRewriteDescendantPathsParsesAndExecutes() {
        // Bulk UPDATE — parameters can't match anything; assert it returns 0.
        int affected = employeeRepository.rewriteDescendantPaths(UNMATCHED_PATH, UNMATCHED_PATH + "-new");
        assertThat(affected).isZero();
    }

    @Test
    void organizationalUnitSubtreeQueryParsesAndExecutes() {
        assertThatCode(() -> organizationalUnitRepository.findSubtree(UNMATCHED_PATH)).doesNotThrowAnyException();
        assertThat(organizationalUnitRepository.findSubtree(UNMATCHED_PATH)).isEmpty();
    }

    @Test
    void organizationalUnitRootsQueryParsesAndExecutes() {
        assertThatCode(() -> organizationalUnitRepository.findAllRoots()).doesNotThrowAnyException();
    }

    @Test
    void organizationalUnitHierarchyRowsByIdsQueryParsesAndExecutes() {
        List<UUID> ids = List.of(UUID.randomUUID());
        assertThatCode(() -> organizationalUnitRepository.findHierarchyRowsByIds(ids)).doesNotThrowAnyException();
        assertThat(organizationalUnitRepository.findHierarchyRowsByIds(ids)).isEmpty();
    }

    @Test
    void organizationalUnitAncestorChainQueryParsesAndExecutes() {
        assertThatCode(() -> organizationalUnitRepository.findAncestorChainByLeafPath(UNMATCHED_PATH)).doesNotThrowAnyException();
        assertThat(organizationalUnitRepository.findAncestorChainByLeafPath(UNMATCHED_PATH)).isEmpty();
    }

    @Test
    void organizationalUnitSubtreeHeadcountsQueryParsesAndExecutes() {
        assertThatCode(() -> organizationalUnitRepository.findSubtreeHeadcounts(UNMATCHED_PATH)).doesNotThrowAnyException();
        assertThat(organizationalUnitRepository.findSubtreeHeadcounts(UNMATCHED_PATH)).isEmpty();
    }

    @Test
    void organizationalUnitRootDirectHeadcountsQueryParsesAndExecutes() {
        assertThatCode(() -> organizationalUnitRepository.findRootDirectHeadcounts()).doesNotThrowAnyException();
    }

    @Test
    void organizationalUnitRewriteDescendantPathsParsesAndExecutes() {
        int affected = organizationalUnitRepository.rewriteDescendantPaths(UNMATCHED_PATH, UNMATCHED_PATH + "-new");
        assertThat(affected).isZero();
    }
}
