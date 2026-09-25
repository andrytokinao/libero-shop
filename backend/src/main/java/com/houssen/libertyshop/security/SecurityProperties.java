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
 * @param allowedOrigins browser origins allowed to call the API with credentials
 * @param secureCookies  send the session and CSRF cookies only over HTTPS
 */
@ConfigurationProperties(prefix = "libertyshop.security")
public record SecurityProperties(List<String> allowedOrigins, boolean secureCookies) {

    public SecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
