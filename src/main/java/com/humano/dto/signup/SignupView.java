package com.humano.dto.signup;

/**
 * A sign-up as its emailed link shows it. {@code stage}: {@code CONFIRM} (choose a name and password
 * to start), {@code SETTING_UP}, {@code READY} (sign in) or {@code FAILED} (confirm again to retry —
 * setup resumes where it stopped).
 */
public record SignupView(Stage stage, String email, String companyName, String subdomain, String planName, int trialDays) {
    public enum Stage {
        CONFIRM,
        SETTING_UP,
        READY,
        FAILED,
    }
}
