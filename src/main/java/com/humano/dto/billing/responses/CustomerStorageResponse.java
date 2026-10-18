package com.humano.dto.billing.responses;

/**
 * A customer's storage as the owner console shows it: the extra storage held, what the plan allows
 * (with the packs) and what is used. {@code usedGb} is {@code null} when the workspace cannot be read
 * (not set up); {@code limitGb} {@code null} = unlimited.
 */
public record CustomerStorageResponse(String planName, StorageAddonResponse addon, Double usedGb, Integer limitGb) {}
