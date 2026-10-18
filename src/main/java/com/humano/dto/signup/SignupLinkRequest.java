package com.humano.dto.signup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The emailed link's token, sent in a body so it stays out of URLs and access logs. */
public record SignupLinkRequest(@NotBlank @Size(max = 2000) String token) {}
