package com.humano.dto.demo;

import com.humano.domain.enumeration.CountryCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The public "Book a demo" form. {@code website} is a honeypot: hidden from people, so a filled-in
 * value means a bot (accepted silently, never stored).
 */
public record SubmitDemoRequest(
    @NotBlank @Size(max = 100) String fullName,
    @NotBlank @Email @Size(max = 254) String workEmail,
    @NotBlank @Size(max = 100) String companyName,
    @Size(max = 20) String companySize,
    CountryCode country,
    @Size(max = 30) String phone,
    @Size(max = 2000) String message,
    @Size(max = 200) String website
) {}
