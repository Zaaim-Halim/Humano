/**
 * Developer-only convenience code, active under the {@code dev} Spring profile.
 * <p>
 * Nothing in this package is wired in production profiles. {@link com.humano.dev.DevDataSeeder} boots a
 * ready-to-use demo tenant with one login per role so the app can be exercised end to end without manual
 * onboarding. Keep production concerns out of here.
 */
package com.humano.dev;
