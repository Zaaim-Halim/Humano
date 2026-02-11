package com.humano.dto.payroll.request;

import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a pay component.
 */
public record CreatePayComponentRequest(
    @NotNull(message = "Code is required") PayComponentCode code,

    @NotNull(message = "Name is required") @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters") String name,

    @NotNull(message = "Kind is required") Kind kind,

    @NotNull(message = "Measurement is required") Measurement measure,

    boolean taxable,

    boolean contributesToSocial,

    boolean percentage,

    Integer calcPhase
) {}
