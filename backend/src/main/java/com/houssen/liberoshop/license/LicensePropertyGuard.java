package com.houssen.liberoshop.license;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.net.URL;
import java.security.CodeSource;
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
 * anywhere other than the classpath, and says so in the log. A value on the classpath is
 * authoritative only while the classes run from a build directory -- that is how a
 * development run and the test resources keep working. In a delivered application the
 * packaged file is not believed either: a jar opens with any archive tool, and one line
 * added to its {@code application.properties} would otherwise do what the command line no
 * longer can. There, every switch is simply on. The point is not to make tampering
 * impossible, it is to make it require changing compiled code.
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

    /**
     * Where a build leaves the classes it has just compiled: what an IDE, Maven and the
     * tests run from, and the only place the packaged file is still believed.
     */
    private static final String BUILD_OUTPUT = "/target/classes/";

    /**
     * This class's name as written in the sources. ProGuard renames the whole license
     * package in the delivered jar and leaves string constants alone, so the name only
     * matches in a build that was never obfuscated: the difference is compiled in.
     */
    private static final String SOURCE_NAME = "com.houssen.liberoshop.license.LicensePropertyGuard";

    /** Decided once: the classes do not move while the application runs. */
    private static final boolean DELIVERED = isDelivered();

    private LicensePropertyGuard() {
    }

    /**
     * Whether these classes run from anything other than a build directory -- the jar, the
     * installed application.
     *
     * <p>The {@code application.properties} inside a jar is one archive tool away from
     * being edited, no decompiler needed, so in a delivered application it is given no more
     * say than the command line. Anything unexpected counts as delivered: the doubt must
     * not be what switches the checks off.
     */
    private static boolean isDelivered() {
        try {
            CodeSource source = LicensePropertyGuard.class.getProtectionDomain().getCodeSource();
            return isDelivered(LicensePropertyGuard.class.getName(), source == null ? null : source.getLocation());
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * The location alone is not enough: the classes of the delivered jar can be extracted
     * into a folder named {@code target/classes} and started from there, with an
     * {@code application.properties} of one's own beside them. Those classes still carry
     * their obfuscated names, though, and renaming them back means rebuilding the
     * application -- which is the line this guard draws.
     *
     * @param className this class's name at run time
     * @param location  where it was loaded from
     */
    static boolean isDelivered(String className, URL location) {
        return !SOURCE_NAME.equals(className)
                || location == null
                || !"file".equals(location.getProtocol())
                || !location.getPath().endsWith(BUILD_OUTPUT);
    }

    /**
     * Restores the packaged value of every guarded key that an external source overrode.
     *
     * <p>Called before anything is bound, so both the early startup check and the beans
     * created later see the same, corrected environment.
     */
    static void pin(ConfigurableEnvironment environment) {
        pin(environment, DELIVERED);
    }

    /**
     * @param delivered whether this is the application as a customer receives it. Then the
     *                  packaged file is not trusted either, and every switch is on
     */
    static void pin(ConfigurableEnvironment environment, boolean delivered) {
        if (environment.getPropertySources().contains(PINNED)) {
            return;
        }
        Map<String, Object> corrections = new LinkedHashMap<>();
        GUARDED.forEach((key, fallback) -> {
            String effective = environment.getProperty(key);
            String packaged = delivered ? null : packagedValue(environment, key);
            if (Objects.equals(effective, packaged)) {
                return;
            }
            String restored = packaged != null ? packaged : fallback;
            if (restored.equals(effective)) {
                return;
            }
            // The line that tells support somebody tried to switch the licensing off from
            // the command line rather than from the application they were given.
            log.warn("Ignoring '{}={}': this setting may only come from the application's own "
                            + "configuration, not from the command line, the environment or an external "
                            + "file, and cannot be changed at all in a delivered application. "
                            + "Using '{}' instead.",
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
