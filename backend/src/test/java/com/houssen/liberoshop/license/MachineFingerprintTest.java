package com.houssen.liberoshop.license;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineFingerprintTest {

    @Test
    @DisplayName("is stable for the same machine")
    void isDeterministic() {
        String first = MachineFingerprint.compute("CAISSE-01", "1A2B-3C4D");
        String second = MachineFingerprint.compute("CAISSE-01", "1A2B-3C4D");

        assertEquals(first, second);
    }

    @Test
    @DisplayName("uses a readable, phone-friendly format")
    void hasReadableFormat() {
        String fingerprint = MachineFingerprint.compute("CAISSE-01", "1A2B-3C4D");

        // LS1-XXXXX-XXXXX-XXXXX-XXXXX with the Crockford alphabet: no I, L, O or U.
        assertTrue(fingerprint.matches("LS1-[0-9A-HJKMNP-TV-Z]{5}(-[0-9A-HJKMNP-TV-Z]{5}){3}"),
                "unexpected format: " + fingerprint);
    }

    @Test
    @DisplayName("ignores case and surrounding whitespace")
    void normalisesInput() {
        // Windows reports COMPUTERNAME upper case, /proc reports it lower case: the same
        // machine must not get two different fingerprints.
        assertEquals(
                MachineFingerprint.compute("CAISSE-01", "1A2B-3C4D"),
                MachineFingerprint.compute("  caisse-01 ", "1a2b-3c4d"));
    }

    @Test
    @DisplayName("changes when the machine changes")
    void distinguishesMachines() {
        String reference = MachineFingerprint.compute("CAISSE-01", "1A2B-3C4D");

        assertNotEquals(reference, MachineFingerprint.compute("CAISSE-02", "1A2B-3C4D"),
                "a different machine name must yield a different fingerprint");
        assertNotEquals(reference, MachineFingerprint.compute("CAISSE-01", "9Z8Y-7X6W"),
                "a different system volume must yield a different fingerprint");
    }

    @Test
    @DisplayName("still produces a value when a probe fails")
    void toleratesMissingInputs() {
        // A locked-down machine may refuse both probes; startup must not break.
        String fingerprint = MachineFingerprint.compute(null, null);

        assertNotNull(fingerprint);
        assertTrue(fingerprint.startsWith(MachineFingerprint.PREFIX + "-"));
        assertEquals(fingerprint, MachineFingerprint.compute("", "   "));
    }

    @Test
    @DisplayName("reads the real machine without throwing")
    void readsCurrentMachine() {
        // Exercises the actual OS probes on the build machine, whatever it runs.
        String fingerprint = MachineFingerprint.current();

        assertNotNull(fingerprint);
        assertTrue(fingerprint.matches("LS1-[0-9A-HJKMNP-TV-Z]{5}(-[0-9A-HJKMNP-TV-Z]{5}){3}"),
                "unexpected format: " + fingerprint);
        assertEquals(fingerprint, MachineFingerprint.current(), "repeated reads must agree");
    }
}
