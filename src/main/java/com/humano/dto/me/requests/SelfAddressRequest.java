package com.humano.dto.me.requests;

import java.util.UUID;

/**
 * Address fields an employee may set on their own record.
 * <p>
 * Deliberately has no {@code employeeId}: the owner is always taken from the security
 * context, so a self-service caller has no field with which to target someone else's
 * record. Null fields are left unchanged on update.
 */
public record SelfAddressRequest(
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
