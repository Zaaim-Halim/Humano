package com.humano.dto.payroll.response;

import java.time.LocalDate;
import java.util.List;

/**
 * A statutory payroll template a new workspace can start with (anonymous: the sign-up form suggests
 * the templates for the chosen country). Only what helps the choice — no rules or sources.
 */
public record PublicPayrollTemplateResponse(
    String id,
    String name,
    String version,
    String status,
    String country,
    String currency,
    LocalDate validFrom,
    LocalDate validTo,
    List<String> payFrequencies,
    List<String> limitations
) {}
