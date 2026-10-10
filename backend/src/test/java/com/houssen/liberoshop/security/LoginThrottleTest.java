package com.houssen.liberoshop.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who waits after wrong passwords, and who must not: the guessing phone, never the till beside
 * it nor the cashier who mistyped twice.
 */
class LoginThrottleTest {

    private static final String PHONE = "192.168.1.50";
    private static final String TILL = "192.168.1.10";

    private final MovingClock clock = new MovingClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);

    @Test
    @DisplayName("Quelques erreurs de frappe ne coutent rien")
    void aFewMistakesAreFree() {
        fail(PHONE, "nadia", LoginThrottle.FREE_FAILURES - 1);

        assertDoesNotThrow(() -> throttle.check(PHONE, "nadia"));
    }

    @Test
    @DisplayName("Au-dela, l'attente double a chaque echec, jusqu'au plafond")
    void theWaitDoubles() {
        fail(PHONE, "nadia", LoginThrottle.FREE_FAILURES);
        assertEquals(LoginThrottle.FIRST_WAIT, waitOf(PHONE, "nadia"));

        throttle.failed(PHONE, "nadia");
        assertEquals(LoginThrottle.FIRST_WAIT.multipliedBy(2), waitOf(PHONE, "nadia"));

        fail(PHONE, "nadia", 20);
        assertEquals(LoginThrottle.LONGEST_WAIT, waitOf(PHONE, "nadia"));
    }

    @Test
    @DisplayName("Le nom est compare sans casse ni espaces")
    void theNameIsFolded() {
        fail(PHONE, "nadia", LoginThrottle.FREE_FAILURES);

        assertThrows(TooManyLoginAttemptsException.class, () -> throttle.check(PHONE, "  NADIA "));
    }

    @Test
    @DisplayName("Le telephone qui devine attend, pas la caisse d'a cote")
    void anotherAddressIsNotLockedOut() {
        fail(PHONE, "nadia", LoginThrottle.FREE_FAILURES + 3);

        assertDoesNotThrow(() -> throttle.check(TILL, "nadia"));
    }

    @Test
    @DisplayName("L'attente ecoulee, on peut reessayer")
    void theWaitEnds() {
        fail(PHONE, "nadia", LoginThrottle.FREE_FAILURES);

        clock.advance(LoginThrottle.FIRST_WAIT);

        assertDoesNotThrow(() -> throttle.check(PHONE, "nadia"));
    }

    @Test
    @DisplayName("Le bon mot de passe remet le compteur a zero")
    void successClearsTheCount() {
        fail(PHONE, "nadia", LoginThrottle.FREE_FAILURES - 1);
        throttle.succeeded(PHONE, "nadia");

        fail(PHONE, "nadia", LoginThrottle.FREE_FAILURES - 1);

        assertDoesNotThrow(() -> throttle.check(PHONE, "nadia"));
    }

    @Test
    @DisplayName("Un compteur laisse au repos est oublie")
    void anOldCountIsForgotten() {
        fail(PHONE, "nadia", LoginThrottle.FREE_FAILURES - 1);
        clock.advance(LoginThrottle.FORGOTTEN_AFTER.plusSeconds(1));

        throttle.failed(PHONE, "nadia");

        assertDoesNotThrow(() -> throttle.check(PHONE, "nadia"));
    }

    @Test
    @DisplayName("Essayer tous les noms depuis une meme adresse finit par la faire attendre")
    void walkingThroughTheNamesIsCaught() {
        for (int i = 0; i < LoginThrottle.FREE_FAILURES_PER_ADDRESS; i++) {
            throttle.failed(PHONE, "compte" + i);
        }

        assertThrows(TooManyLoginAttemptsException.class, () -> throttle.check(PHONE, "encore-un-autre"));
        assertDoesNotThrow(() -> throttle.check(TILL, "encore-un-autre"));
    }

    @Test
    @DisplayName("Le message dit combien de temps attendre")
    void theMessageSaysHowLong() {
        fail(PHONE, "nadia", LoginThrottle.FREE_FAILURES);

        TooManyLoginAttemptsException refused =
                assertThrows(TooManyLoginAttemptsException.class, () -> throttle.check(PHONE, "nadia"));

        assertTrue(refused.getMessage().contains("30 secondes"), refused.getMessage());
    }

    private void fail(String address, String username, int times) {
        for (int i = 0; i < times; i++) {
            throttle.failed(address, username);
        }
    }

    private Duration waitOf(String address, String username) {
        return assertThrows(TooManyLoginAttemptsException.class, () -> throttle.check(address, username))
                .waitTime();
    }

    private static final class MovingClock extends Clock {

        private Instant now = Instant.parse("2026-01-05T08:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
