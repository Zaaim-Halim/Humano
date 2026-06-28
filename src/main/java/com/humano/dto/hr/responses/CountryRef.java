package com.humano.dto.hr.responses;

import com.humano.domain.shared.Country;
import java.util.UUID;

/**
 * Minimal embedded reference to a country. On reads it carries {@code id} + ISO {@code code} +
 * {@code name}; on writes only {@code id} is read.
 */
public record CountryRef(UUID id, String code, String name) {
    public static CountryRef of(Country c) {
        return c == null ? null : new CountryRef(c.getId(), c.getCode() == null ? null : c.getCode().name(), c.getName());
    }
}
