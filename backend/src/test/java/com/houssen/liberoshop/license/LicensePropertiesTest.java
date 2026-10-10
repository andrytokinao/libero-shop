package com.houssen.liberoshop.license;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The evaluation period is the one number a customer would gain most from editing, so the
 * cap that stops them is worth a test of its own.
 */
class LicensePropertiesTest {

    @Test
    @DisplayName("caps a trial length the configuration tried to inflate")
    void capsAnInflatedTrial() {
        // What a developer who has read application.properties would write: five and a
        // half years of evaluation, from a single command line flag.
        LicenseProperties.TrialProperties trial =
                new LicenseProperties.TrialProperties(true, 2000, null, null);

        assertEquals(LicenseProperties.TrialProperties.MAX_DAYS, trial.days());
    }

    @Test
    @DisplayName("lets the configuration shorten the trial")
    void allowsAShorterTrial() {
        // Shortening only ever works against whoever sets it, so it stays configurable.
        assertEquals(7, new LicenseProperties.TrialProperties(true, 7, null, null).days());
    }

    @Test
    @DisplayName("refuses a zero or negative trial length")
    void refusesANonPositiveTrial() {
        assertEquals(1, new LicenseProperties.TrialProperties(true, 0, null, null).days());
        assertEquals(1, new LicenseProperties.TrialProperties(true, -30, null, null).days());
    }

    @Test
    @DisplayName("grants the full period when nothing is configured")
    void defaultsToTheMaximum() {
        assertEquals(LicenseProperties.TrialProperties.MAX_DAYS,
                new LicenseProperties.TrialProperties(null, null, null, null).days());
    }
}
