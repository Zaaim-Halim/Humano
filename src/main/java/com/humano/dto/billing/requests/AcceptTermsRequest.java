package com.humano.dto.billing.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Accepting the Terms of Service and DPA in {@code version}: the one the page showed. */
public record AcceptTermsRequest(@NotBlank @Size(max = 20) String version) {}
