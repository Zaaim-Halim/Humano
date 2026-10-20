package com.humano.service.signup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.humano.domain.enumeration.CountryCode;
import com.humano.service.errors.BadRequestAlertException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tech.jhipster.config.JHipsterProperties;

/**
 * The emailed sign-up link carries the sign-up itself, signed, so nothing is stored before the
 * email is confirmed: {@code base64url(json).base64url(HMAC-SHA256)}, keyed with the application's
 * remember-me secret. A link whose signature does not match is refused; {@link Payload#expiresAt()}
 * bounds how long it can start a workspace (it keeps working afterwards to follow the setup).
 */
@Component
public class SignupLink {

    private static final String ENTITY_NAME = "signup";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** What the visitor filled in on the public form. */
    public record Payload(
        String email,
        String companyName,
        String subdomain,
        CountryCode country,
        String timezone,
        UUID planId,
        Instant expiresAt
    ) {
        public boolean expired() {
            return expiresAt == null || expiresAt.isBefore(Instant.now());
        }
    }

    private final ObjectMapper objectMapper;
    private final byte[] key;

    public SignupLink(ObjectMapper objectMapper, JHipsterProperties jHipsterProperties) {
        this.objectMapper = objectMapper;
        this.key = ("signup:" + jHipsterProperties.getSecurity().getRememberMe().getKey()).getBytes(StandardCharsets.UTF_8);
    }

    public String issue(Payload payload) {
        try {
            String body = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            return body + "." + ENCODER.encodeToString(sign(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write the sign-up link", e);
        }
    }

    /** The link's sign-up, whether or not it has expired; refuses a link that was tampered with. */
    public Payload read(String token) {
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot <= 0) {
            throw invalid();
        }
        String body = token.substring(0, dot);
        try {
            byte[] signature = DECODER.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(signature, sign(body))) {
                throw invalid();
            }
            return objectMapper.readValue(DECODER.decode(body), Payload.class);
        } catch (IllegalArgumentException | java.io.IOException e) {
            throw invalid();
        }
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static BadRequestAlertException invalid() {
        return new BadRequestAlertException("This link is not valid", ENTITY_NAME, "invalidlink");
    }
}
