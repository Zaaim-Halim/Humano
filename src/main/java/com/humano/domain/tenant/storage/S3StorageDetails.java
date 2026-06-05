package com.humano.domain.tenant.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.humano.domain.enumeration.storage.StorageBackendType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Config for the S3 storage backend.
 * <p>
 * Credentials should never be returned by REST endpoints — annotate the response DTO to omit
 * them. Persistence-time encryption (e.g. {@code TenantPasswordCipher}) should be applied at
 * the service layer before this record reaches the entity; this record itself is a plain
 * value object so it remains trivially (de)serializable.
 *
 * @param bucket             S3 bucket name.
 * @param region             AWS region (e.g. {@code us-east-1}).
 * @param accessKeyId        IAM access key id; null/blank if the deployment uses an instance
 *                           profile or web-identity federation.
 * @param secretAccessKey    matching secret; null/blank when accessKeyId is null.
 * @param maxFileSizeBytes   per-file upper bound. S3 supports 5&nbsp;GiB single-PUT and 5&nbsp;TiB
 *                           via multipart — we cap at 5&nbsp;GiB by default; raise once multipart
 *                           upload is wired in.
 */
public record S3StorageDetails(
    @NotBlank String bucket,
    @NotBlank String region,
    @JsonIgnore String accessKeyId,
    @JsonIgnore String secretAccessKey,
    @Positive long maxFileSizeBytes
)
    implements StorageConfigDetails {
    @Override
    public StorageBackendType type() {
        return StorageBackendType.S3;
    }
}
