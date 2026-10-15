package com.houssen.liberoshop.update;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.LocalTime;

/**
 * How the application updates itself: {@code liberoshop.update.*}.
 *
 * <p>The manual page (upload a {@code .lsu} file, or press "check now") is always available to an
 * administrator and needs none of these. These settings only govern the <em>automatic</em>
 * channel, which is off until an administrator both enables it and gives a URL.
 *
 * @param enabled              master switch for automatic checks. Off by default: an install
 *                             never reaches out on its own until asked to.
 * @param url                  where to fetch the update manifest from. Empty disables the online
 *                             check and download (and therefore automatic updates) regardless of
 *                             {@code enabled}; the upload page still works.
 * @param checkTime            the time of day the automatic check runs, once a day. Chosen to
 *                             default outside trading hours.
 * @param requireConfirmation  whether an automatic update waits for every connected screen to
 *                             agree before it cuts the server off. On by default, so a till is
 *                             never interrupted without someone at it saying so.
 * @param confirmationTimeout  how long to wait for those agreements before giving up and leaving
 *                             the update for the next check.
 */
@ConfigurationProperties(prefix = "liberoshop.update")
public record UpdateProperties(
        boolean enabled,
        String url,
        LocalTime checkTime,
        boolean requireConfirmation,
        Duration confirmationTimeout) {

    private static final LocalTime DEFAULT_CHECK_TIME = LocalTime.of(3, 0);
    private static final Duration DEFAULT_CONFIRMATION_TIMEOUT = Duration.ofMinutes(5);

    public UpdateProperties {
        url = url == null ? "" : url.trim();
        checkTime = checkTime == null ? DEFAULT_CHECK_TIME : checkTime;
        confirmationTimeout = confirmationTimeout == null || confirmationTimeout.isNegative() || confirmationTimeout.isZero()
                ? DEFAULT_CONFIRMATION_TIMEOUT : confirmationTimeout;
    }

    /** True when a manifest URL is configured, the precondition for any online operation. */
    public boolean hasUrl() {
        return !url.isBlank();
    }

    /** True when the automatic channel is both switched on and has somewhere to look. */
    public boolean automaticEnabled() {
        return enabled && hasUrl();
    }
}
