package com.humano.domain.enumeration.storage;

import java.util.Set;

/**
 * Policy bundle that drives upload validation per business purpose.
 * <p>
 * Each context carries:
 * <ul>
 *   <li>{@code maxBytes} — per-file upper bound enforced before any backend write.</li>
 *   <li>{@code allowedContentTypes} — MIME types accepted; checked against the uploaded
 *       file's declared {@code Content-Type} (do not trust this alone — a sniff/probe at the
 *       service layer is recommended for sensitive contexts).</li>
 *   <li>{@code defaultVisibility} — initial visibility if the upload request does not set one.
 *       Callers may override per upload subject to role checks.</li>
 * </ul>
 * <p>
 * Limits are deliberately conservative; the real cap a tenant can use is
 * {@code min(context.maxBytes(), backend.capabilities().maxObjectSize())} so a DATABASE-backed
 * tenant can't upload a 50&nbsp;MiB contract even if the context permits it.
 */
public enum FileContext {
    EMPLOYEE_AVATAR(2L * 1024 * 1024, Set.of("image/jpeg", "image/png", "image/webp"), FileVisibility.INTERNAL),
    EMPLOYEE_CV(10L * 1024 * 1024, Set.of("application/pdf"), FileVisibility.PRIVATE),
    EMPLOYEE_DOCUMENT(20L * 1024 * 1024, Set.of("application/pdf", "image/jpeg", "image/png"), FileVisibility.PRIVATE),
    EMPLOYMENT_CONTRACT(20L * 1024 * 1024, Set.of("application/pdf"), FileVisibility.PRIVATE),
    PAYSLIP(2L * 1024 * 1024, Set.of("application/pdf"), FileVisibility.PRIVATE),
    EXPENSE_RECEIPT(5L * 1024 * 1024, Set.of("application/pdf", "image/jpeg", "image/png"), FileVisibility.PRIVATE),
    COMPANY_LOGO(1L * 1024 * 1024, Set.of("image/png", "image/svg+xml", "image/jpeg"), FileVisibility.PUBLIC),
    FINANCIAL_DOCUMENT(50L * 1024 * 1024, Set.of("application/pdf"), FileVisibility.PRIVATE),
    OTHER(20L * 1024 * 1024, Set.of(), FileVisibility.PRIVATE);

    private final long maxBytes;
    private final Set<String> allowedContentTypes;
    private final FileVisibility defaultVisibility;

    FileContext(long maxBytes, Set<String> allowedContentTypes, FileVisibility defaultVisibility) {
        this.maxBytes = maxBytes;
        this.allowedContentTypes = allowedContentTypes;
        this.defaultVisibility = defaultVisibility;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public Set<String> allowedContentTypes() {
        return allowedContentTypes;
    }

    public FileVisibility defaultVisibility() {
        return defaultVisibility;
    }

    /** Empty set on a context (e.g. {@link #OTHER}) means "anything goes". */
    public boolean isContentTypeAllowed(String contentType) {
        return allowedContentTypes.isEmpty() || allowedContentTypes.contains(contentType);
    }
}
