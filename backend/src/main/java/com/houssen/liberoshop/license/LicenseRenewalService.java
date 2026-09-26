package com.houssen.liberoshop.license;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Periodically asks the publisher's server for a renewed license.
 *
 * <p>Built for a shop with an unreliable internet link, so every design choice favours
 * staying up over staying current:
 * <ul>
 *   <li>a failed call is a debug line, never an error the user sees;</li>
 *   <li>the local file keeps working untouched when the server is unreachable;</li>
 *   <li>a downloaded license is verified before it can replace the installed one;</li>
 *   <li>nothing here can ever revoke or shorten an existing license.</li>
 * </ul>
 *
 * <p>Cadence: one attempt every {@code intervalDays} (21 by default) while the license is
 * comfortably valid, then one attempt per day once expiry is within
 * {@code urgentWithinDays}. A shop that is offline for a month still renews on the first
 * day the link comes back.
 */
public class LicenseRenewalService {

    private static final Logger log = LoggerFactory.getLogger(LicenseRenewalService.class);

    /** How often the scheduler ticks. The interval logic decides whether to actually call. */
    private static final long TICK_MILLIS = 24L * 60 * 60 * 1000;

    /** Let the application finish starting before touching the network. */
    private static final long INITIAL_DELAY_MILLIS = 5L * 60 * 1000;

    private final LicenseService licenseService;
    private final LicenseProperties.RenewalProperties properties;
    private final RestClient restClient;
    private final Clock clock;

    /** Last successful contact with the server; {@code null} until the first one. */
    private volatile LocalDate lastSuccessfulCheck;

    public LicenseRenewalService(LicenseService licenseService, LicenseProperties.RenewalProperties properties,
                                 RestClient restClient, Clock clock) {
        this.licenseService = licenseService;
        this.properties = properties;
        this.restClient = restClient;
        this.clock = clock;
    }

    /** Scheduled entry point. Decides whether it is time to call, then calls. */
    @Scheduled(initialDelay = INITIAL_DELAY_MILLIS, fixedDelay = TICK_MILLIS)
    public void checkForRenewal() {
        if (shouldCheckNow()) {
            tryRenew();
        }
    }

    /**
     * Performs one renewal attempt.
     *
     * <p>Public so the customer can trigger it from the UI the moment they have paid,
     * instead of waiting for the next tick.
     *
     * @return {@code true} if a new license was downloaded and installed
     */
    public boolean tryRenew() {
        LicenseStatus current = licenseService.findStatus().orElse(null);
        if (current == null) {
            log.debug("Skipping renewal check: no valid local license to renew.");
            return false;
        }
        if (current.isTrial()) {
            // There is no subscription behind a trial, so there is nothing for the server
            // to look up. The customer installs their first license through /install.
            log.debug("Skipping renewal check: the installation is running on its trial period.");
            return false;
        }
        try {
            String response = restClient.post()
                    .uri(properties.endpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .body(new RenewalRequest(
                            current.license().licenseId(),
                            current.license().customerId(),
                            licenseService.machineFingerprint(),
                            current.license().expiresOn().toString()))
                    .retrieve()
                    .body(String.class);

            // The server answers with nothing at all when the customer has not renewed yet.
            if (response == null || response.isBlank()) {
                lastSuccessfulCheck = today();
                log.debug("Renewal check completed: no new license available.");
                return false;
            }

            LicenseStatus installed = licenseService.install(response.getBytes(StandardCharsets.UTF_8));
            lastSuccessfulCheck = today();
            log.info("License renewed automatically, now valid until {}.", installed.license().expiresOn());
            return true;
        } catch (RuntimeException e) {
            // Offline, DNS failure, server down, HTTP error, unusable payload: all the
            // same outcome. The installed license stays in force.
            log.debug("Renewal check failed, keeping the local license: {}", e.toString());
            return false;
        }
    }

    /** Whether enough time has passed, or expiry is close enough to warrant a daily try. */
    private boolean shouldCheckNow() {
        if (!properties.enabled() || properties.endpoint() == null || properties.endpoint().isBlank()) {
            return false;
        }
        LicenseStatus current = licenseService.findStatus().orElse(null);
        if (current == null || current.isTrial()) {
            return false;
        }
        if (current.daysUntilExpiry() <= properties.urgentWithinDays()) {
            return true;
        }
        if (lastSuccessfulCheck == null) {
            return true;
        }
        return ChronoUnit.DAYS.between(lastSuccessfulCheck, today()) >= properties.intervalDays();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * What the publisher's endpoint receives. It carries no personal or commercial data:
     * just enough to look the subscription up and to sign the reply for the right machine.
     */
    public record RenewalRequest(String licenseId, String customerId, String machineFingerprint,
                                 String currentExpiry) {
    }
}
