package com.houssen.liberoshop.license;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stops the enforcement switches from being turned off from outside the jar.
 *
 * <p>Every knob in {@link LicenseProperties} is read through Spring Boot's ordinary
 * configuration mechanism, and that mechanism deliberately lets external sources win over
 * the file packaged in the application. Useful everywhere else, fatal here: the three
 * switches below each disable a control on their own, and none of them needs a decompiler.
 *
 * <pre>
 * java -jar backend.jar --liberoshop.license.enabled=false
 * LIBEROSHOP_LICENSE_ENABLED=false java -jar backend.jar
 * echo liberoshop.license.enabled=false &gt; ./config/application.properties
 * </pre>
 *
 * <p>The application even prints the name of the property in its own log when it starts
 * unlicensed, so no guesswork is involved either.
 *
 * <p>This guard restores the packaged value whenever one of those keys is supplied from
 * anywhere other than the classpath, and says so in the log. A value inside the jar is
 * still authoritative -- that is how a development build and the test resources keep
 * working -- but changing it there means repacking the application, which is the level of
 * effort this module has always declared out of scope. The difference matters: the point
 * is not to make tampering impossible, it is to stop a one-line command from doing what
 * should require rebuilding the product.
 *
 * <p>Only switches are pinned, not paths. Relocating the license directory is a legitimate
 * deployment choice and buys nothing on its own: the trial date lives in several places at
 * once and the earliest one wins. {@code trial.days} is not pinned either -- it is capped
 * by {@link LicenseProperties.TrialProperties#MAX_DAYS} at binding time, so configuration
 * can only ever shorten the evaluation period.
 */
final class LicensePropertyGuard {

    private static final Logger log = LoggerFactory.getLogger(LicensePropertyGuard.class);

    /** Name of the property source this guard installs, also its idempotence marker. */
    private static final String PINNED = "liberoshop-license-pinned";

    /**
     * The keys whose weakening value must not come from outside, mapped to the value to
     * fall back on when the packaged configuration says nothing at all.
     */
    private static final Map<String, String> GUARDED = Map.of(
            "liberoshop.license.enabled", "true",
            "liberoshop.license.clock-guard.enabled", "true",
            "liberoshop.license.trial.system-wide", "true");

    private LicensePropertyGuard() {
    }

    /**
     * Restores the packaged value of every guarded key that an external source overrode.
     *
     * <p>Called before anything is bound, so both the early startup check and the beans
     * created later see the same, corrected environment.
     */
    static void pin(ConfigurableEnvironment environment) {
        if (environment.getPropertySources().contains(PINNED)) {
            return;
        }
        Map<String, Object> corrections = new LinkedHashMap<>();
        GUARDED.forEach((key, fallback) -> {
            String effective = environment.getProperty(key);
            String packaged = packagedValue(environment, key);
            if (Objects.equals(effective, packaged)) {
                return;
            }
            String restored = packaged != null ? packaged : fallback;
            // The line that tells support somebody tried to switch the licensing off from
            // the command line rather than from the application they were given.
            log.warn("Ignoring '{}={}': this setting may only come from the application's own "
                            + "configuration, not from the command line, the environment or an external "
                            + "file. Using '{}' instead.",
                    key, effective, restored);
            corrections.put(key, restored);
        });
        if (!corrections.isEmpty()) {
            // Ahead of everything, including commandLineArgs, which Spring puts first.
            environment.getPropertySources().addFirst(new MapPropertySource(PINNED, corrections));
        }
    }

    /**
     * The value as the packaged application states it, ignoring every source that a
     * customer can supply without opening the jar.
     */
    private static String packagedValue(ConfigurableEnvironment environment, String key) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!isPackaged(source)) {
                continue;
            }
            Object value = source.getProperty(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * Whether a property source lives inside the application.
     *
     * <p>Spring Boot names config data sources after the resource they came from, so an
     * {@code application.properties} shipped in the jar reads {@code class path resource
     * [application.properties]} while one dropped next to it reads {@code file
     * [config/application.properties]}. Command line arguments, system properties and
     * environment variables have their own names and none of them match.
     */
    private static boolean isPackaged(PropertySource<?> source) {
        return source.getName().contains("class path resource");
    }
}
