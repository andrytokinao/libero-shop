package com.houssen.libertyshop.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Deployment-specific security switches.
 *
 * <p>Kept in configuration rather than hard-coded so the same jar runs behind
 * {@code ng serve} during development and behind a single origin in production, where
 * {@code allowed-origins} is simply left empty.
 *
 * <p>Token signing and lifetime live in {@link JwtProperties}, under
 * {@code libertyshop.security.jwt}.
 *
 * @param allowedOrigins browser origins allowed to call the API
 */
@ConfigurationProperties(prefix = "libertyshop.security")
public record SecurityProperties(List<String> allowedOrigins) {

    public SecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
