package com.humano.config;

/**
 * Application constants.
 */
public final class Constants {

    // Regex for acceptable logins
    public static final String LOGIN_REGEX = "^(?>[a-zA-Z0-9!$&*+=?^_`{|}~.-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*)|(?>[_.@A-Za-z0-9-]+)$";

    public static final String SYSTEM = "system";
    public static final String DEFAULT_LANGUAGE = "en";

    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 100;

    /**
     * Version of the Terms of Service and Data Processing Addendum a sign-up accepts: the date they
     * last changed. Must match {@code LEGAL_VERSION} in {@code features/legal/legal-info.ts}; bump both
     * together, so a form still showing older terms is refused rather than recorded as accepting these.
     */
    public static final String TERMS_VERSION = "2026-10-28";

    /**
     * Days' notice customers get before a new sub-processor starts processing their data (Data
     * Processing Addendum §3). Must match {@code LEGAL.subprocessorNoticeDays} in {@code legal-info.ts}.
     */
    public static final int SUBPROCESSOR_NOTICE_DAYS = 30;

    private Constants() {}
}
