package com.houssen.liberoshop.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Signing key and lifetime of the access tokens.
 *
 * <p>The secret may be left empty: a random key is then generated at start-up, which is
 * exactly what a developer wants and what a customer installation must not do -- a restart
 * would sign with a different key and log everyone out. See {@code application.properties}.
 *
 * @param secret shared secret used for HS256, at least {@value #MIN_SECRET_BYTES} bytes,
 *               or empty to sign with a key generated at start-up
 * @param ttl    how long a token stays valid; there is no refresh token, so this is the
 *               whole length of a working session
 * @param issuer value of the {@code iss} claim, checked again on every incoming token
 */
@ConfigurationProperties(prefix = "liberoshop.security.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {

    /** HS256 is defined over a 256-bit key; Nimbus refuses anything shorter. */
    public static final int MIN_SECRET_BYTES = 32;

    /** Long enough for a full shift at the till, since running out means signing in again. */
    private static final Duration DEFAULT_TTL = Duration.ofHours(12);

    private static final String DEFAULT_ISSUER = "libero-shop";

    public JwtProperties {
        secret = secret == null ? "" : secret.trim();
        ttl = ttl == null ? DEFAULT_TTL : ttl;
        issuer = issuer == null || issuer.isBlank() ? DEFAULT_ISSUER : issuer.trim();

        // Refused here rather than at the first login: a key that cannot sign is a
        // deployment mistake, and the application must not start pretending otherwise.
        if (!secret.isEmpty() && secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("liberoshop.security.jwt.secret doit faire au moins "
                    + MIN_SECRET_BYTES + " octets pour signer en HS256.");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "liberoshop.security.jwt.ttl doit etre strictement positif.");
        }
    }

    /** True when no secret is configured and the key has to be generated at start-up. */
    public boolean hasGeneratedKey() {
        return secret.isEmpty();
    }
}
