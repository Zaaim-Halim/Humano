package com.humano.dto.me.requests;

import jakarta.validation.constraints.NotBlank;

/**
 * Emergency-contact fields an employee may set on their own record.
 * <p>
 * Deliberately has no {@code employeeId}: the owner is always taken from the security
 * context, so a self-service caller has no field with which to target someone else's
 * record. Other null fields are left unchanged on update.
 */
public record SelfEmergencyContactRequest(
    @NotBlank(message = "Name is required") String name,
    String relationship,
    String phone,
    String email
) {}
