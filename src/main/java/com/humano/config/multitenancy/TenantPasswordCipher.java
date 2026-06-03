package com.humano.config.multitenancy;

import java.security.SecureRandom;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.stereotype.Component;

/**
 * Symmetric encryption + secure generation of tenant database passwords (P1.4).
 *
 * <p>Encryption is delegated to the auto-configured {@link StringEncryptor} from
 * {@code jasypt-spring-boot-starter} (PBE; key sourced from {@code jasypt.encryptor.password}
 * which in turn binds to the {@code JASYPT_ENCRYPTOR_PASSWORD} env var — see
 * {@code application.yml}).
 *
 * <p>The encrypted form is stored in {@code tenant_database_config.db_password} and decrypted
 * once at {@code TenantDataSourceProvider#createDataSource} when building each tenant's Hikari
 * pool, so the plaintext exists only inside the JVM and never on disk in the master DB.
 *
 * <p><b>Rotation:</b> change {@code JASYPT_ENCRYPTOR_PASSWORD}, then iterate every
 * {@code tenant_database_config} row, decrypting with the old key (boot the app with both keys
 * temporarily — keep the old as {@code JASYPT_ENCRYPTOR_OLD_PASSWORD} and re-encrypt with the
 * new one). For v1 this is a manual SOP; we'll automate it when a customer rotates.
 */
@Component
public class TenantPasswordCipher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+[]{};:,.<>?";
    private static final int GENERATED_LENGTH = 32;

    private final StringEncryptor encryptor;

    public TenantPasswordCipher(StringEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    /** Generate a 32-character cryptographically-random tenant DB password. */
    public String generatePassword() {
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Encrypt a plaintext password for storage in {@code tenant_database_config.db_password}. */
    public String encrypt(String plaintext) {
        return encryptor.encrypt(plaintext);
    }

    /** Decrypt a stored ciphertext back to the plaintext password Hikari needs. */
    public String decrypt(String ciphertext) {
        return encryptor.decrypt(ciphertext);
    }
}
