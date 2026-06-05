package com.humano.domain.enumeration.storage;

/**
 * Access level a stored file is exposed at.
 *
 * <ul>
 *   <li>{@link #PRIVATE}  — only the owner (and explicitly authorized roles) can read.</li>
 *   <li>{@link #INTERNAL} — any authenticated user belonging to the same tenant.</li>
 *   <li>{@link #PUBLIC}   — anyone with the file's public token; safe to serve unauthenticated
 *                          (e.g. behind a CDN). Use sparingly.</li>
 * </ul>
 */
public enum FileVisibility {
    PRIVATE,
    INTERNAL,
    PUBLIC,
}
