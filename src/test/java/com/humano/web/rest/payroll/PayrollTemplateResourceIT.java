package com.humano.web.rest.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.humano.IntegrationTest;
import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import com.humano.domain.shared.Authority;
import com.humano.domain.shared.Employee;
import com.humano.domain.shared.Permission;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.repository.shared.PermissionRepository;
import com.humano.security.AuthoritiesConstants;
import com.humano.security.AuthorityPermissionService;
import com.humano.security.DefaultRolePermissions;
import com.humano.service.billing.entitlement.EntitlementService;
import com.humano.service.billing.entitlement.Entitlements;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.tenancy.MultiTenantProperties;
import com.humano.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP tests for the payroll template endpoints employee profiles go through, run through the real
 * permission check and plan gate: a payroll admin lists a template's employees and sets details for
 * several at once; an empty bulk request is refused; an employee without the payroll-admin
 * permission can't read or change anything; and a plan without payroll leaves the list readable but
 * refuses changes. Requests go to the platform tenant (the test database), whose catalog holds
 * platform roles only, so each test grants the workspace catalog for the roles it uses. Rolls back.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional("tenantTransactionManager")
@WithMockUser(authorities = { AuthoritiesConstants.PAYROLL_ADMIN })
class PayrollTemplateResourceIT {

    private static final String EMPLOYEES = "/api/payroll/templates/nl-employment/employees";
    private static final String BY_EMPLOYEE = "/api/payroll/templates/by-employee/";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private OrganizationalUnitRepository units;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private CountryRepository countries;

    @Autowired
    private AuthorityRepository authorities;

    @Autowired
    private PermissionRepository permissions;

    @Autowired
    private AuthorityPermissionService permissionCache;

    @Autowired
    private MultiTenantProperties tenancy;

    @MockitoBean
    private EntitlementService entitlements;

    private Employee hire;

    @BeforeEach
    void setUp() {
        grantTheWorkspaceCatalog(AuthoritiesConstants.PAYROLL_ADMIN, AuthoritiesConstants.EMPLOYEE);
        when(entitlements.current()).thenReturn(Entitlements.UNLIMITED);

        OrganizationalUnit unit = new OrganizationalUnit();
        unit.setName("Utrecht " + UUID.randomUUID());
        unit.setType(OrganizationalUnitType.TEAM);
        unit = units.saveAndFlush(unit);
        Position position = new Position();
        position.setName("Baker " + UUID.randomUUID());
        position.setLevel("L2");
        position = positions.saveAndFlush(position);

        String login = "nl-" + UUID.randomUUID().toString().substring(0, 8);
        Employee e = new Employee();
        e.setLogin(login);
        e.setEmail(login + "@example.com");
        e.setPassword("x".repeat(60));
        e.setActivated(true);
        e.setFirstName("Sem");
        e.setLastName("Bakker");
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setStartDate(LocalDate.of(2026, 1, 1));
        e.setBirthDate(LocalDate.of(1998, 7, 21));
        e.setCountry(countries.findAll().stream().filter(c -> c.getCode() == CountryCode.NL).findFirst().orElseThrow());
        e.setPosition(position);
        e.setUnit(unit);
        hire = employees.saveAndFlush(e);
    }

    @AfterEach
    void forgetTheWorkspaceCatalog() {
        // The grants roll back with the test; drop the cached copy so later tests see the real catalog.
        evictPermissionCache();
    }

    @Test
    void aPayrollAdminSeesWhatEachEmployeeLacksAndSetsItForSeveralAtOnce() throws Exception {
        String listed = mvc.perform(get(EMPLOYEES)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode row = rowOf(json.readTree(listed), hire.getId());
        assertThat(row.get("name").asText()).isEqualTo("Sem Bakker");
        assertThat(texts(row.get("missing"))).containsExactly("applyLoonheffingskorting", "awfPremium");

        mvc
            .perform(patch(EMPLOYEES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bulk(List.of(hire.getId()), "LOW")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].attributes.awfPremium").value("LOW"))
            .andExpect(jsonPath("$[0].missing[0]").value("applyLoonheffingskorting"))
            .andExpect(jsonPath("$[0].missing.length()").value(1));
    }

    @Test
    void anEmployeesPageListsTheSwitchedOnTemplatesThatCoverThem() throws Exception {
        mvc
            .perform(
                put("/api/payroll/templates/nl-employment/adoption")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":true,\"settings\":{\"aofPremium\":\"HIGH\",\"whkRate\":\"0.0152\"}}")
            )
            .andExpect(status().isOk());

        mvc
            .perform(get(BY_EMPLOYEE + hire.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].templateId").value("nl-employment"))
            .andExpect(jsonPath("$[0].employee.missing.length()").value(2));
    }

    @Test
    void anEmptyBulkRequestIsRefused() throws Exception {
        mvc
            .perform(patch(EMPLOYEES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bulk(List.of(), "LOW")))
            .andExpect(status().isBadRequest());
        mvc
            .perform(
                patch(EMPLOYEES)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"employeeIds\":[\"" + hire.getId() + "\"],\"attributes\":{}}")
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = { AuthoritiesConstants.EMPLOYEE })
    void anEmployeeWithoutThePayrollAdminPermissionCanNotReadOrChangeProfiles() throws Exception {
        mvc.perform(get(EMPLOYEES)).andExpect(status().isForbidden());
        mvc.perform(get(BY_EMPLOYEE + hire.getId())).andExpect(status().isForbidden());
        mvc
            .perform(patch(EMPLOYEES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bulk(List.of(hire.getId()), "LOW")))
            .andExpect(status().isForbidden());
        mvc
            .perform(
                put(EMPLOYEES + "/" + hire.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"attributes\":{\"awfPremium\":\"LOW\"}}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void aPlanWithoutPayrollKeepsTheListReadableButRefusesChanges() throws Exception {
        when(entitlements.current()).thenReturn(
            new Entitlements(
                UUID.randomUUID(),
                "Starter",
                Map.of(FeatureCodes.PAYROLL, new Entitlements.Label(UUID.randomUUID(), "Payroll", null)),
                Map.of()
            )
        );

        mvc.perform(get(EMPLOYEES)).andExpect(status().isOk());
        mvc
            .perform(patch(EMPLOYEES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bulk(List.of(hire.getId()), "LOW")))
            .andExpect(status().isForbidden());
        mvc
            .perform(
                put("/api/payroll/templates/nl-employment/adoption")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":false,\"settings\":{}}")
            )
            .andExpect(status().isForbidden());
    }

    private static JsonNode rowOf(JsonNode list, UUID employeeId) {
        for (JsonNode row : list) {
            if (row.get("employeeId").asText().equals(employeeId.toString())) return row;
        }
        throw new AssertionError("employee " + employeeId + " not listed");
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static String bulk(List<UUID> ids, String awfPremium) {
        String list = String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList());
        return "{\"employeeIds\":[" + list + "],\"attributes\":{\"awfPremium\":\"" + awfPremium + "\"}}";
    }

    private void grantTheWorkspaceCatalog(String... roles) {
        for (String role : roles) {
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

    private void evictPermissionCache() {
        TenantContext.setCurrentTenant(tenancy.getPlatformTenant());
        try {
            permissionCache.evictCurrentTenant();
        } finally {
            TenantContext.clear();
        }
    }
}
