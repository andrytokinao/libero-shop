package com.houssen.liberoshop.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Signing key and lifetime of the access tokens.
 *
 * <p>Three ways to get a key, tried in this order. A {@code secret} written in the
 * configuration, for whoever administers the machine and wants to hold it. Else a
 * {@code secretFile}: the installation generates its own key at its first start and keeps
 * it there, which is what a shop with nobody to edit a configuration needs. Else a random
 * key generated at each start-up -- fine for a test, wrong anywhere people work, since a
 * restart then signs with a different key and logs everyone out. See
 * {@code application.properties}.
 *
 * @param secret     shared secret used for HS256, at least {@value #MIN_SECRET_BYTES} bytes,
 *                   or empty to fall back on {@code secretFile}
 * @param secretFile where the installation keeps the key it generated for itself; empty for
 *                   none, and ignored when {@code secret} is set
 * @param ttl        how long a token stays valid; there is no refresh token, so this is the
 *                   whole length of a working session
 * @param issuer     value of the {@code iss} claim, checked again on every incoming token
 */
@ConfigurationProperties(prefix = "liberoshop.security.jwt")
public record JwtProperties(String secret, String secretFile, Duration ttl, String issuer) {

    /** HS256 is defined over a 256-bit key; Nimbus refuses anything shorter. */
    public static final int MIN_SECRET_BYTES = 32;

    /** Long enough for a full shift at the till, since running out means signing in again. */
    private static final Duration DEFAULT_TTL = Duration.ofHours(12);

    private static final String DEFAULT_ISSUER = "libero-shop";

    // Named as the one to bind with: a second constructor below would otherwise leave Spring
    // looking for a no-argument one.
    @ConstructorBinding
    public JwtProperties {
        secret =secret == null ? "" : secret.trim();
        secretFile = secretFile == null ? "" : secretFile.trim();
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

    /** No key file: the two ways there were before installations kept their own key. */
    public JwtProperties(String secret, Duration ttl, String issuer) {
        this(secret, "", ttl, issuer);
    }

    /** True when the configuration holds no secret and the key has to come from elsewhere. */
    public boolean hasGeneratedKey() {
        return secret.isEmpty();
    }

    /** True when the key is the one this installation generated and kept in a file. */
    public boolean keepsItsKeyInAFile() {
        return secret.isEmpty() && !secretFile.isEmpty();
    }
}
