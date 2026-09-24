package com.houssen.libertyshop.license;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuration of the license module, prefix {@code libertyshop.license}.
 *
 * <p>Only operational knobs live here -- where the file is, whether to phone home. The
 * trust anchor (the public key) is compiled in, so no configuration can make the
 * application accept a license the publisher did not sign.
 *
 * <p>The switches are a different matter and are not self-protecting: {@link #enabled} set
 * to {@code false} skips every check, and a trial length read straight from configuration
 * would be no length at all. Two things keep that in hand. The evaluation period is capped
 * by {@link TrialProperties#MAX_DAYS}, compiled in, so configuration can only shorten it.
 * And {@link LicensePropertyGuard} restores the packaged value of every switch that an
 * external source -- command line, environment variable, a file dropped next to the jar --
 * tried to override. What remains is the value shipped inside the application itself,
 * which cannot be changed without repacking it.
 *
 * @param enabled    master switch; keep it {@code true} on customer installations and set
 *                   it to {@code false} in development and tests. Only honoured from the
 *                   application's own configuration, never from an external override
 * @param path       where the signed {@code .lic} file lives
 * @param clockGuard rollback protection settings
 * @param renewal    online renewal settings
 * @param trial      one-off evaluation period granted to an unlicensed installation
 */
@ConfigurationProperties(prefix = "libertyshop.license")
public record LicenseProperties(
        Boolean enabled,
        Path path,
        ClockGuardProperties clockGuard,
        RenewalProperties renewal,
        TrialProperties trial) {

    public LicenseProperties {
        enabled = enabled == null || enabled;
        path = path == null ? Path.of("./license/liberty-shop.lic") : path;
        clockGuard = clockGuard == null ? new ClockGuardProperties(null, null) : clockGuard;
        renewal = renewal == null ? new RenewalProperties(null, null, null, null, null) : renewal;
        trial = trial == null ? new TrialProperties(null, null, null, null) : trial;
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

    /**
     * Evaluation period for an installation with no license file.
     *
     * <p>Switching it off is the strict setting -- an unlicensed installation then refuses
     * to start, as it did before the trial existed. There is deliberately no knob that
     * makes the trial longer than what {@code days} says on a machine that has already
     * used it: the record written by {@code TrialRegistry} decides that, not this file.
     *
     * @param enabled    whether a missing license falls back to a trial instead of refusing to start
     * @param days       length of the evaluation period, first day included
     * @param path       location of the record kept beside the license; defaults to
     *                   {@code .license-trial} next to the {@code .lic}
     * @param systemWide whether to replicate the record outside the installation directory
     *                   (user profile, machine-wide data directory, Windows registry).
     *                   Keep it on: it is what makes the trial non-repeatable. Tests turn
     *                   it off so they never touch the developer's profile or registry.
     */
    public record TrialProperties(Boolean enabled, Integer days, Path path, Boolean systemWide) {

        /**
         * The longest evaluation period this build will ever grant, whatever any
         * configuration file says.
         *
         * <p>Compiled in for the same reason the public key is: a duration that could be
         * raised from {@code application.properties} would not be a limit at all.
         * {@code --libertyshop.license.trial.days=2000} is a one-line command, needs no
         * decompiler, and unlike {@code enabled=false} it leaves nothing in the log that
         * looks wrong -- the application would simply report a trial running until 2032.
         * Configuration may still <em>shorten</em> the period, which can only ever work
         * against whoever sets it.
         */
        public static final int MAX_DAYS = 90;

        public TrialProperties {
            enabled = enabled == null || enabled;
            days = days == null ? MAX_DAYS : Math.max(1, Math.min(MAX_DAYS, days));
            systemWide = systemWide == null || systemWide;
        }
    }

    /** Resolved location of the clock guard state file. */
    public Path resolvedClockGuardPath() {
        return beside(clockGuard.path(), ".license-state");
    }

    /** Resolved location of the trial record kept next to the license. */
    public Path resolvedTrialPath() {
        return beside(trial.path(), ".license-trial");
    }

    private Path beside(Path configured, String defaultName) {
        if (configured != null) {
            return configured;
        }
        Path parent = path.toAbsolutePath().getParent();
        return parent == null ? Path.of(defaultName) : parent.resolve(defaultName);
    }
}
