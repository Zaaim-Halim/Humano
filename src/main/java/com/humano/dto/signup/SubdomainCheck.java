package com.humano.dto.signup;

/** Whether a workspace address can be used; {@code reason} is {@code invalid}, {@code reserved} or {@code taken} when not. */
public record SubdomainCheck(String subdomain, boolean available, String reason) {}
