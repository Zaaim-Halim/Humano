package com.humano.web.rest.hr.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.humano.IntegrationTest;
import com.humano.config.multitenancy.MultiTenantProperties;
import com.humano.config.multitenancy.TenantContext;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import com.humano.domain.shared.Authority;
import com.humano.domain.shared.Employee;
import com.humano.domain.shared.Permission;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.repository.shared.PermissionRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.security.AuthorityPermissionService;
import com.humano.security.DefaultRolePermissions;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP tests for {@link EmployeeLifecycleResource} and {@link OnboardingChecklistResource}, run
 * through the real permission checks. Requests go to the platform tenant (backed by the test
 * database), whose own catalog holds platform roles only, so each test grants the business
 * workspace catalog ({@link DefaultRolePermissions}) for the roles it uses. As
 * the workspace admin: start, list, complete and cancel an onboarding, see it in history, and have
 * the employee's status follow; a plain employee may not manage onboarding; bad checklists are
 * refused. Each test rolls back.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional("tenantTransactionManager")
@WithMockUser(authorities = { "ROLE_ADMIN" })
class EmployeeLifecycleResourceIT {

    private static final String ONBOARDING = "/api/hr/workflows/onboarding";
    private static final String CHECKLIST = "/api/hr/workflows/onboarding-checklist";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private OrganizationalUnitRepository units;

    @Autowired
    private AuthorityRepository authorities;

    @Autowired
    private PermissionRepository permissions;

    @Autowired
    private AuthorityPermissionService permissionCache;

    @Autowired
    private MultiTenantProperties tenancy;

    private Employee hire;

    @BeforeEach
    void grantTheWorkspaceCatalog() {
        for (String role : List.of(AuthoritiesConstants.ADMIN, AuthoritiesConstants.EMPLOYEE)) {
            Authority authority = authorities
                .findById(role)
                .orElseGet(() -> {
                    Authority a = new Authority();
                    a.setName(role);
                    return authorities.save(a);
                });
            for (String name : DefaultRolePermissions.rolePermissions().getOrDefault(role, Set.of())) {
                Permission permission = permissions
                    .findById(name)
                    .orElseGet(() -> {
                        Permission p = new Permission();
                        p.setName(name);
                        return permissions.save(p);
                    });
                if (authority.getPermissions().stream().noneMatch(p -> p.getName().equals(name))) {
                    authority.getPermissions().add(permission);
                }
            }
            authorities.saveAndFlush(authority);
        }
        evictPermissionCache();
    }

    @AfterEach
    void forgetTheWorkspaceCatalog() {
        // The grants roll back with the test; drop the cached copy so later tests see the real catalog.
        evictPermissionCache();
    }

    private void evictPermissionCache() {
        TenantContext.setCurrentTenant(tenancy.getPlatformTenant());
        try {
            permissionCache.evictCurrentTenant();
        } finally {
            TenantContext.clear();
        }
    }

    @BeforeEach
    void createNewHire() {
        OrganizationalUnit unit = new OrganizationalUnit();
        unit.setName("Mobile Team " + UUID.randomUUID());
        unit.setType(OrganizationalUnitType.TEAM);
        unit = units.saveAndFlush(unit);

        Position position = new Position();
        position.setName("Mobile Engineer " + UUID.randomUUID());
        position.setLevel("L2");
        position = positions.saveAndFlush(position);

        String login = "hire-" + UUID.randomUUID().toString().substring(0, 8);
        hire = new Employee();
        hire.setLogin(login);
        hire.setEmail(login + "@example.com");
        hire.setPassword("x".repeat(60));
        hire.setActivated(true);
        hire.setFirstName("Ines");
        hire.setLastName("Duarte");
        hire.setStatus(EmployeeStatus.ACTIVE);
        hire.setStartDate(LocalDate.now());
        hire.setPosition(position);
        hire.setUnit(unit);
        hire = employees.saveAndFlush(hire);
    }

    @Test
    void theAdminRunsAnOnboardingToTheEnd() throws Exception {
        JsonNode started = start(LocalDate.now().plusDays(7));
        String processId = started.get("processId").asText();
        assertThat(started.get("tasks")).isNotEmpty();
        assertThat(employees.findById(hire.getId()).orElseThrow().getStatus()).isEqualTo(EmployeeStatus.ONBOARDING);

        mvc
            .perform(get(ONBOARDING))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.processId == '" + processId + "')]").exists());

        JsonNode last = started;
        for (JsonNode task : started.get("tasks")) {
            last = read(
                mvc
                    .perform(
                        post(ONBOARDING + "/" + processId + "/tasks/complete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("taskId", task.get("taskId").asText(), "successful", true)))
                    )
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString()
            );
        }
        assertThat(last.get("processStatus").asText()).isEqualTo("COMPLETED");
        assertThat(employees.findById(hire.getId()).orElseThrow().getStatus()).isEqualTo(EmployeeStatus.ACTIVE);

        mvc
            .perform(get(ONBOARDING).param("status", "COMPLETED", "CANCELLED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.processId == '" + processId + "')].status").value("COMPLETED"));
        mvc
            .perform(get(ONBOARDING).param("employeeId", hire.getId().toString()).param("status", "COMPLETED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cancellingNeedsAReasonAndRestoresTheStatus() throws Exception {
        String processId = start(null).get("processId").asText();

        mvc.perform(post(ONBOARDING + "/" + processId + "/cancel").with(csrf())).andExpect(status().isBadRequest());
        mvc
            .perform(post(ONBOARDING + "/" + processId + "/cancel").param("reason", "Offer withdrawn").with(csrf()))
            .andExpect(status().isNoContent());

        mvc.perform(get(ONBOARDING + "/" + processId)).andExpect(status().isOk()).andExpect(jsonPath("$.processStatus").value("CANCELLED"));
        assertThat(employees.findById(hire.getId()).orElseThrow().getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void aSecondActiveOnboardingIsRefused() throws Exception {
        start(null);

        mvc
            .perform(
                post(ONBOARDING)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("employeeId", hire.getId().toString())))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = { "ROLE_EMPLOYEE" })
    void aPlainEmployeeCannotStartOnboarding() throws Exception {
        mvc
            .perform(
                post(ONBOARDING)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("employeeId", hire.getId().toString())))
            )
            .andExpect(status().isForbidden());
        mvc
            .perform(put(CHECKLIST).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(checklist(14, 3)))
            .andExpect(status().isForbidden());
    }

    @Test
    void theChecklistRefusesTasksPastItsLengthAndAnEmptyList() throws Exception {
        mvc
            .perform(put(CHECKLIST).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(checklist(7, 14)))
            .andExpect(status().isBadRequest());
        mvc
            .perform(
                put(CHECKLIST)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("defaultDays", 14, "tasks", List.of())))
            )
            .andExpect(status().isBadRequest());

        mvc
            .perform(put(CHECKLIST).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(checklist(14, 3)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customized").value(true))
            .andExpect(jsonPath("$.defaultDays").value(14));
    }

    private JsonNode start(LocalDate targetDate) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(
            Map.of("employeeId", hire.getId().toString(), "startDate", LocalDate.now().toString())
        );
        if (targetDate != null) body.put("targetDate", targetDate.toString());
        return read(
            mvc
                .perform(post(ONBOARDING).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
        );
    }

    private String checklist(int defaultDays, int dueDay) throws Exception {
        return json.writeValueAsString(
            Map.of(
                "defaultDays",
                defaultDays,
                "tasks",
                List.of(Map.of("title", "Laptop handover", "dueDay", dueDay, "owner", "HR", "link", "ACCOUNT"))
            )
        );
    }

    private JsonNode read(String body) throws Exception {
        return json.readTree(body);
    }
}
