package com.houssen.libertyshop.license;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuration of the license module, prefix {@code libertyshop.license}.
 *
 * <p>Only operational knobs live here -- where the file is, whether to phone home. The
 * trust anchor (the public key) and the enforcement rules are compiled in, so editing
 * {@code application.properties} can never turn an expired installation back into a
 * licensed one.
 *
 * @param enabled    master switch; keep it {@code true} on customer installations and set
 *                   it to {@code false} in development and tests
 * @param path       where the signed {@code .lic} file lives
 * @param clockGuard rollback protection settings
 * @param renewal    online renewal settings
 */
@ConfigurationProperties(prefix = "libertyshop.license")
public record LicenseProperties(
        Boolean enabled,
        Path path,
        ClockGuardProperties clockGuard,
        RenewalProperties renewal) {

    public LicenseProperties {
        enabled = enabled == null || enabled;
        path = path == null ? Path.of("./license/liberty-shop.lic") : path;
        clockGuard = clockGuard == null ? new ClockGuardProperties(null, null) : clockGuard;
        renewal = renewal == null ? new RenewalProperties(null, null, null, null, null) : renewal;
    }

    /**
     * @param enabled whether to track the latest date seen, defeating clock rollback
     * @param path    state file location; defaults to {@code .license-state} next to the license
     */
    public record ClockGuardProperties(Boolean enabled, Path path) {
        public ClockGuardProperties {
            enabled = enabled == null || enabled;
        }
    }

    /**
     * @param enabled      whether to contact the publisher at all
     * @param endpoint     HTTPS URL that returns a renewed {@code .lic} for a paid customer
     * @param intervalDays how often to try while the license is comfortably valid
     * @param urgentWithinDays once this close to expiry (or past it), try on every daily tick
     * @param timeout      per-attempt network timeout; kept short so a dead link never
     *                     slows the application down
     */
    public record RenewalProperties(
            Boolean enabled,
            String endpoint,
            Integer intervalDays,
            Integer urgentWithinDays,
            Duration timeout) {

        public RenewalProperties {
            enabled = enabled != null && enabled;
            intervalDays = intervalDays == null ? 21 : intervalDays;
            urgentWithinDays = urgentWithinDays == null ? 30 : urgentWithinDays;
            timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        }
    }

    /** Resolved location of the clock guard state file. */
    public Path resolvedClockGuardPath() {
        if (clockGuard.path() != null) {
            return clockGuard.path();
        }
        Path parent = path.toAbsolutePath().getParent();
        return parent == null ? Path.of(".license-state") : parent.resolve(".license-state");
    }
}
