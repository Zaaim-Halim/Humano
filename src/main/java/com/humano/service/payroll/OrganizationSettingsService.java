package com.humano.service.payroll;

import com.humano.domain.payroll.OrganizationSettings;
import com.humano.dto.payroll.request.UpdateOrganizationSettingsRequest;
import com.humano.dto.payroll.response.OrganizationSettingsResponse;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.payroll.OrganizationSettingsRepository;
import com.humano.repository.payroll.PayrollCalendarRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company-level payroll policy ({@link OrganizationSettings}) — a singleton per
 * tenant schema. {@link #get()} returns the saved row or transient defaults
 * (never persists); {@link #update} upserts the single row.
 */
@Service
@Transactional
public class OrganizationSettingsService {

    private final OrganizationSettingsRepository repository;
    private final CurrencyRepository currencyRepository;
    private final PayrollCalendarRepository payrollCalendarRepository;

    public OrganizationSettingsService(
        OrganizationSettingsRepository repository,
        CurrencyRepository currencyRepository,
        PayrollCalendarRepository payrollCalendarRepository
    ) {
        this.repository = repository;
        this.currencyRepository = currencyRepository;
        this.payrollCalendarRepository = payrollCalendarRepository;
    }

    /**
     * Current settings, or transient defaults (entity field initializers) when
     * none has been saved yet. Read-only — does not persist a row.
     */
    @Transactional(readOnly = true)
    public OrganizationSettingsResponse get() {
        return repository.findAll().stream().findFirst().map(this::toResponse).orElseGet(() -> toResponse(new OrganizationSettings()));
    }

    /** Replace the single settings row, creating it on first save. */
    public OrganizationSettingsResponse update(UpdateOrganizationSettingsRequest request) {
        OrganizationSettings settings = repository.findAll().stream().findFirst().orElseGet(OrganizationSettings::new);

        settings.setStandardHoursPerDay(request.standardHoursPerDay());
        settings.setStandardHoursPerWeek(request.standardHoursPerWeek());
        settings.setStandardMonthlyHours(request.standardMonthlyHours());
        settings.setDefaultBasis(request.defaultBasis());
        settings.setDefaultOvertimeMultiplier(request.defaultOvertimeMultiplier());
        settings.setTimezone(request.timezone());

        settings.setDefaultCurrency(
            request.defaultCurrencyId() == null
                ? null
                : currencyRepository
                    .findById(request.defaultCurrencyId())
                    .orElseThrow(() -> new EntityNotFoundException("Currency not found: " + request.defaultCurrencyId()))
        );
        settings.setDefaultPayrollCalendar(
            request.defaultPayrollCalendarId() == null
                ? null
                : payrollCalendarRepository
                    .findById(request.defaultPayrollCalendarId())
                    .orElseThrow(() -> new EntityNotFoundException("Payroll calendar not found: " + request.defaultPayrollCalendarId()))
        );

        return toResponse(repository.save(settings));
    }

    private OrganizationSettingsResponse toResponse(OrganizationSettings s) {
        return new OrganizationSettingsResponse(
            s.getId(),
            s.getStandardHoursPerDay(),
            s.getStandardHoursPerWeek(),
            s.getStandardMonthlyHours(),
            s.getDefaultBasis(),
            s.getDefaultOvertimeMultiplier(),
            s.getTimezone(),
            s.getDefaultCurrency() == null ? null : s.getDefaultCurrency().getId(),
            s.getDefaultCurrency() == null ? null : String.valueOf(s.getDefaultCurrency().getCode()),
            s.getDefaultPayrollCalendar() == null ? null : s.getDefaultPayrollCalendar().getId(),
            s.getDefaultPayrollCalendar() == null ? null : s.getDefaultPayrollCalendar().getName()
        );
    }
}
