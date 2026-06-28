package com.humano.dto.hr.responses;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO record for returning a Address record.
 */
public record AddressResponse(
    UUID id,
    UUID employeeId,
    String type,
    String street,
    String building,
    String apartment,
    String city,
    String state,
    String postalCode,
    UUID countryId,
    Boolean primary,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate
) {}
