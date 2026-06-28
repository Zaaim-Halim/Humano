package com.humano.dto.hr.requests;

import java.util.UUID;

/**
 * DTO record for partially updating a Address record. Null fields are left unchanged.
 */
public record UpdateAddressRequest(
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
