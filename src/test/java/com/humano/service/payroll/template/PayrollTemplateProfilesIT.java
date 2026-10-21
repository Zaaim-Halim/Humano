package com.humano.service.payroll.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.humano.IntegrationTest;
import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.ConfigurePayrollTemplateRequest;
import com.humano.dto.payroll.request.UpdateTemplateProfilesRequest;
import com.humano.dto.payroll.response.TemplateEmployeeResponse;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employees' profiles for a payroll template, as the template's employee list reads and writes them:
 * who the Dutch template covers, which required attributes each still lacks, and setting the same
 * attributes on several employees at once. Rolled back after each test.
 */
@IntegrationTest
@Transactional("tenantTransactionManager")
class PayrollTemplateProfilesIT {

    private static final String NL_TEMPLATE = "nl-employment";

    @Autowired
    private PayrollTemplateService templateService;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private OrganizationalUnitRepository units;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private CountryRepository countries;

    private OrganizationalUnit unit;
    private Position position;
    private Employee withoutProfile;
    private Employee partlyFilledIn;
    private Employee leftBeforeTheRules;
    private Employee leftThisYear;
    private Employee inGermany;

    @BeforeEach
    void setUp() {
        unit = new OrganizationalUnit();
        unit.setName("Amsterdam " + UUID.randomUUID());
        unit.setType(OrganizationalUnitType.TEAM);
        unit = units.saveAndFlush(unit);
        position = new Position();
        position.setName("Baker " + UUID.randomUUID());
        position.setLevel("L2");
        position = positions.saveAndFlush(position);

        withoutProfile = employee("Anouk", CountryCode.NL, EmployeeStatus.ACTIVE, null);
        partlyFilledIn = employee("Bram", CountryCode.NL, EmployeeStatus.ACTIVE, null);
        leftBeforeTheRules = employee("Cas", CountryCode.NL, EmployeeStatus.TERMINATED, LocalDate.of(2025, 6, 30));
        leftThisYear = employee("Eva", CountryCode.NL, EmployeeStatus.TERMINATED, LocalDate.of(2026, 3, 31));
        inGermany = employee("Dieter", CountryCode.DE, EmployeeStatus.ACTIVE, null);
        templateService.saveProfile(NL_TEMPLATE, partlyFilledIn.getId(), Map.of("applyLoonheffingskorting", "true"));
    }

    @Test
    void listsCoveredEmployeesWithWhatTheyStillLack() {
        Map<UUID, TemplateEmployeeResponse> listed = ours(templateService.employees(NL_TEMPLATE));

        // Not someone who left before the 2026 rules, nor anyone employed in another country.
        assertThat(listed).containsOnlyKeys(withoutProfile.getId(), partlyFilledIn.getId(), leftThisYear.getId());
        assertThat(listed.get(withoutProfile.getId()).missing()).containsExactly("applyLoonheffingskorting", "awfPremium");
        assertThat(listed.get(withoutProfile.getId()).attributes()).isEmpty();
        assertThat(listed.get(partlyFilledIn.getId()).missing()).containsExactly("awfPremium");
        assertThat(listed.get(partlyFilledIn.getId()).name()).isEqualTo("Bram Jansen");
    }

    @Test
    void setsTheSameValuesOnSeveralProfilesKeepingTheirOtherValues() {
        List<TemplateEmployeeResponse> updated = templateService.updateProfiles(
            NL_TEMPLATE,
            new UpdateTemplateProfilesRequest(List.of(withoutProfile.getId(), partlyFilledIn.getId()), Map.of("awfPremium", "LOW"))
        );

        assertThat(updated).hasSize(2);
        Map<UUID, TemplateEmployeeResponse> listed = ours(templateService.employees(NL_TEMPLATE));
        assertThat(listed.get(withoutProfile.getId()).attributes()).containsExactly(Map.entry("awfPremium", "LOW"));
        assertThat(listed.get(withoutProfile.getId()).missing()).containsExactly("applyLoonheffingskorting");
        assertThat(listed.get(partlyFilledIn.getId()).attributes()).containsOnly(
            Map.entry("applyLoonheffingskorting", "true"),
            Map.entry("awfPremium", "LOW")
        );
        assertThat(listed.get(partlyFilledIn.getId()).missing()).isEmpty();
    }

    @Test
    void refusesEmployeesTheTemplateDoesNotCoverAndSavesNothing() {
        var request = new UpdateTemplateProfilesRequest(List.of(withoutProfile.getId(), inGermany.getId()), Map.of("awfPremium", "LOW"));

        assertThatThrownBy(() -> templateService.updateProfiles(NL_TEMPLATE, request))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("1 of the chosen employees are not covered");
        assertThat(ours(templateService.employees(NL_TEMPLATE)).get(withoutProfile.getId()).attributes()).isEmpty();
    }

    @Test
    void refusesAValueTheTemplateDoesNotAccept() {
        var request = new UpdateTemplateProfilesRequest(List.of(withoutProfile.getId()), Map.of("awfPremium", "MEDIUM"));

        assertThatThrownBy(() -> templateService.updateProfiles(NL_TEMPLATE, request))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("awfPremium");
    }

    @Test
    void refusesAnAttributeTheTemplateDoesNotRead() {
        var request = new UpdateTemplateProfilesRequest(List.of(withoutProfile.getId()), Map.of("taxCode", "1257L"));

        assertThatThrownBy(() -> templateService.updateProfiles(NL_TEMPLATE, request))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("taxCode");
    }

    @Test
    void enablingTheTemplateWarnsAboutEmployeesMissingRequiredDetails() {
        var result = templateService.configure(
            NL_TEMPLATE,
            new ConfigurePayrollTemplateRequest(true, Map.of("aofPremium", "HIGH", "whkRate", "0.0152"))
        );

        assertThat(result.warnings()).anyMatch(w -> w.contains("NL employee(s) are missing required details for this template"));
    }

    /** Only this test's employees: the shared test database may hold other Dutch employees. */
    private Map<UUID, TemplateEmployeeResponse> ours(List<TemplateEmployeeResponse> listed) {
        List<UUID> ids = List.of(
            withoutProfile.getId(),
            partlyFilledIn.getId(),
            leftBeforeTheRules.getId(),
            leftThisYear.getId(),
            inGermany.getId()
        );
        return listed
            .stream()
            .filter(e -> ids.contains(e.employeeId()))
            .collect(Collectors.toMap(TemplateEmployeeResponse::employeeId, Function.identity()));
    }

    private Employee employee(String firstName, CountryCode country, EmployeeStatus status, LocalDate endDate) {
        String login = "nl-" + UUID.randomUUID().toString().substring(0, 8);
        Employee e = new Employee();
        e.setLogin(login);
        e.setEmail(login + "@example.com");
        e.setPassword("x".repeat(60));
        e.setActivated(true);
        e.setFirstName(firstName);
        e.setLastName("Jansen");
        e.setStatus(status);
        e.setStartDate(LocalDate.of(2024, 1, 1));
        e.setEndDate(endDate);
        e.setBirthDate(LocalDate.of(1990, 4, 12));
        e.setCountry(countries.findAll().stream().filter(c -> c.getCode() == country).findFirst().orElseThrow());
        e.setPosition(position);
        e.setUnit(unit);
        return employees.saveAndFlush(e);
    }
}
