package com.humano.dto.hr.requests;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO record for creating a Address record.
 */
public record CreateAddressRequest(
    @NotNull(message = "employeeId is required") UUID employeeId,
    String type,
    String street,
    String building,
    String apartment,
    String city,
    String state,
    String postalCode,
    UUID countryId,
    Boolean primary
) {}
