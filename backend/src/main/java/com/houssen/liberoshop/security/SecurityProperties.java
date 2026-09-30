package com.houssen.liberoshop.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deployment-specific security switches.
 *
 * <p>Kept in configuration rather than hard-coded so the same jar runs behind
 * {@code ng serve} during development and behind a single origin in production, where
 * {@code allowed-origins} is simply left empty.
 *
 * <p>Token signing and lifetime live in {@link JwtProperties}, under
 * {@code liberoshop.security.jwt}.
 *
 * @param allowedOrigins browser origins allowed to call the API -- development only
 * @param mobileOrigins  the origins the mobile app's web view presents. The app's pages are
 *                       bundled in the phone, not served by this backend, so every call it makes
 *                       is cross-origin. Kept apart from {@code allowedOrigins} because, unlike
 *                       them, they belong in production.
 */
@ConfigurationProperties(prefix = "liberoshop.security")
public record SecurityProperties(List<String> allowedOrigins, List<String> mobileOrigins) {

    public SecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        mobileOrigins = mobileOrigins == null ? List.of() : List.copyOf(mobileOrigins);
    }

    /** Every cross-origin caller admitted, by the REST API and the WebSocket alike. */
    public List<String> crossOrigins() {
        Set<String> all = new LinkedHashSet<>(allowedOrigins);
        all.addAll(mobileOrigins);
        return List.copyOf(all);
    }
}
