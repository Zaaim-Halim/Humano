package com.humano.service.payroll.template.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A parameter value in force from {@code from} to {@code to} (inclusive), with its source id. */
public record DatedValue(LocalDate from, LocalDate to, BigDecimal value, String source) {
    public boolean covers(LocalDate start, LocalDate end) {
        return !start.isBefore(from) && !end.isAfter(to);
    }
}
