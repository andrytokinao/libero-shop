package com.houssen.liberoshop.license;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The switches that disable enforcement must not be reachable with a command line flag.
 *
 * <p>Each test builds an environment the way Spring Boot does -- a source standing for the
 * {@code application.properties} inside the jar, and one standing for whatever the person
 * launching the application supplied -- then checks which value survives.
 */
class LicensePropertyGuardTest {

    private static final String ENABLED = "liberoshop.license.enabled";
    private static final String CLOCK_GUARD = "liberoshop.license.clock-guard.enabled";
    private static final String SYSTEM_WIDE = "liberoshop.license.trial.system-wide";

    /** Spring Boot's name for config data read from the classpath, i.e. from the jar. */
    private static final String PACKAGED = "Config resource 'class path resource [application.properties]'";

    @Test
    @DisplayName("restores the packaged value when the command line switches licensing off")
    void restoresThePackagedValue() {
        StandardEnvironment environment = environment(
                Map.of(ENABLED, "true"),
                Map.of(ENABLED, "false"));

        LicensePropertyGuard.pin(environment);

        assertEquals("true", environment.getProperty(ENABLED));
    }

    @Test
    @DisplayName("restores every guarded switch, not just the master one")
    void restoresEveryGuardedSwitch() {
        StandardEnvironment environment = environment(
                Map.of(ENABLED, "true", CLOCK_GUARD, "true", SYSTEM_WIDE, "true"),
                Map.of(ENABLED, "false", CLOCK_GUARD, "false", SYSTEM_WIDE, "false"));

        LicensePropertyGuard.pin(environment);

        assertEquals("true", environment.getProperty(ENABLED));
        assertEquals("true", environment.getProperty(CLOCK_GUARD));
        assertEquals("true", environment.getProperty(SYSTEM_WIDE));
    }

    @Test
    @DisplayName("falls back to the safe value when the application ships no value at all")
    void fallsBackToTheSafeValue() {
        // Nothing packaged: the guard must not take the external value by default.
        StandardEnvironment environment = environment(Map.of(), Map.of(ENABLED, "false"));

        LicensePropertyGuard.pin(environment);

        assertEquals("true", environment.getProperty(ENABLED));
    }

    @Test
    @DisplayName("honours a value that comes from the application's own configuration")
    void honoursThePackagedValue() {
        // How a development build and the test resources switch the checks off: the value
        // is inside the application, so changing it means rebuilding it.
        StandardEnvironment environment = environment(Map.of(ENABLED, "false"), Map.of());

        LicensePropertyGuard.pin(environment);

        assertEquals("false", environment.getProperty(ENABLED));
    }

    @Test
    @DisplayName("does not believe the packaged file either in a delivered application")
    void ignoresThePackagedValueOnceDelivered() {
        // The same line as above, but written into the jar with an archive tool.
        StandardEnvironment environment = environment(
                Map.of(ENABLED, "false", CLOCK_GUARD, "false", SYSTEM_WIDE, "false"), Map.of());

        LicensePropertyGuard.pin(environment, true);

        assertEquals("true", environment.getProperty(ENABLED));
        assertEquals("true", environment.getProperty(CLOCK_GUARD));
        assertEquals("true", environment.getProperty(SYSTEM_WIDE));
    }

    @Test
    @DisplayName("adds nothing to a delivered application that sets no switch")
    void leavesADeliveredApplicationAlone() {
        StandardEnvironment environment = environment(Map.of(), Map.of());

        LicensePropertyGuard.pin(environment, true);

        assertFalse(environment.getPropertySources().contains("liberoshop-license-pinned"));
    }

    @Test
    @DisplayName("leaves an untouched environment alone")
    void leavesAnUntouchedEnvironmentAlone() {
        StandardEnvironment environment = environment(Map.of(ENABLED, "true"), Map.of());

        LicensePropertyGuard.pin(environment);

        assertEquals("true", environment.getProperty(ENABLED));
        assertFalse(environment.getPropertySources().contains("liberoshop-license-pinned"),
                "nothing was overridden, so no correction source should have been added");
    }

    @Test
    @DisplayName("does not undo itself when run twice")
    void isIdempotent() {
        StandardEnvironment environment = environment(Map.of(ENABLED, "true"), Map.of(ENABLED, "false"));

        LicensePropertyGuard.pin(environment);
        LicensePropertyGuard.pin(environment);

        assertEquals("true", environment.getProperty(ENABLED));
    }

    @Test
    @DisplayName("only the never-obfuscated classes of a build directory count as development")
    void developmentOnlyFromUnobfuscatedBuildOutput() throws Exception {
        String sourceName = "com.houssen.liberoshop.license.LicensePropertyGuard";
        URL buildOutput = URI.create("file:/D:/shop/backend/target/classes/").toURL();

        assertFalse(LicensePropertyGuard.isDelivered(sourceName, buildOutput));
        assertTrue(LicensePropertyGuard.isDelivered("com.houssen.liberoshop.a.b", buildOutput),
                "classes taken out of the delivered jar keep their renamed names, wherever they are put");
        assertTrue(LicensePropertyGuard.isDelivered(sourceName,
                URI.create("jar:file:/D:/shop/libero-shop.jar!/BOOT-INF/classes/").toURL()));
        assertTrue(LicensePropertyGuard.isDelivered(sourceName, null));
    }

    @Test
    @DisplayName("the tests themselves run as development")
    void testsRunAsDevelopment() {
        assertEquals("com.houssen.liberoshop.license.LicensePropertyGuard", LicensePropertyGuard.class.getName());
    }

    /**
     * @param packaged what the {@code application.properties} inside the jar says
     * @param external what the command line, the environment or a dropped-in file says
     */
    private static StandardEnvironment environment(Map<String, String> packaged, Map<String, String> external) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(PACKAGED, new LinkedHashMap<>(packaged)));
        // addFirst twice: the external source ends up ahead, which is precisely the
        // precedence rule this guard exists to reverse for these keys.
        environment.getPropertySources()
                .addFirst(new MapPropertySource("commandLineArgs", new LinkedHashMap<>(external)));
        return environment;
    }
}
