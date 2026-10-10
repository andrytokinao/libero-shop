package com.houssen.liberoshop.license;

import com.houssen.liberoshop.license.exception.LicenseExpiredException;
import com.houssen.liberoshop.license.exception.LicenseInvalidException;
import com.houssen.liberoshop.license.exception.LicenseNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static com.houssen.liberoshop.license.TestLicenses.DEFAULT_FINGERPRINT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** File handling, degradation, renewal installation and clock rollback. */
class LicenseServiceTest {

    private static final LocalDate EXPIRY = LocalDate.of(2027, 1, 1);
    private static final ZoneId ZONE = ZoneOffset.UTC;

    @TempDir
    Path installDirectory;

    private KeyPair publisherKeys;
    private Path licensePath;

    @BeforeEach
    void setUp() {
        publisherKeys = TestLicenses.generateKeyPair();
        licensePath = installDirectory.resolve("libero-shop.lic");
    }

    @Test
    @DisplayName("falls back to the trial period when no license is installed")
    void grantsTrialWhenUnlicensed() {
        LocalDate firstRun = LocalDate.of(2026, 6, 1);

        LicenseStatus status = service(firstRun).getStatus();

        assertEquals(LicenseState.TRIAL, status.state());
        assertTrue(status.isTrial());
        assertTrue(status.writesAllowed());
        assertEquals(LicensePlan.TRIAL, status.license().plan());
        assertEquals(firstRun.plusDays(29), status.license().expiresOn(), "30 days, first day included");
        // The customer still has to be told which fingerprint to send.
        assertTrue(status.userMessage().contains(DEFAULT_FINGERPRINT));
    }

    @Test
    @DisplayName("turns read-only once the trial period is over")
    void trialEndsAfterItsDays() {
        LocalDate firstRun = LocalDate.of(2026, 6, 1);
        assertEquals(LicenseState.TRIAL, service(firstRun).getStatus().state());

        LicenseService later = service(firstRun.plusDays(30));

        assertEquals(LicenseState.TRIAL_EXPIRED, later.getStatus().state());
        assertFalse(later.isWriteAllowed());
        assertThrows(LicenseExpiredException.class, later::assertWriteAllowed);
    }

    @Test
    @DisplayName("does not open a new trial when an installed license is deleted")
    void deletingTheLicenseDoesNotReopenTheTrial() throws IOException {
        // Day one: properly licensed. The trial window starts ticking all the same.
        writeLicense(TestLicenses.license(publisherKeys).expiresOn(EXPIRY).build());
        LocalDate firstRun = EXPIRY.minusYears(1);
        assertEquals(LicenseState.ACTIVE, service(firstRun).getStatus().state());

        // Months later the customer finds the license folder and empties it.
        Files.delete(licensePath);
        LicenseService afterDeletion = service(firstRun.plusDays(90));

        assertEquals(LicenseState.TRIAL_EXPIRED, afterDeletion.getStatus().state(),
                "the evaluation period was consumed on the day of installation");
        assertFalse(afterDeletion.isWriteAllowed());
    }

    @Test
    @DisplayName("accepts a first license that expires before the trial would have")
    void installsAShortLicenseWhileOnTrial() {
        LocalDate firstRun = LocalDate.of(2026, 6, 1);
        LicenseService service = service(firstRun);
        assertEquals(LicenseState.TRIAL, service.getStatus().state());

        byte[] shortLicense = TestLicenses.license(publisherKeys)
                .issuedOn(firstRun)
                .expiresOn(firstRun.plusDays(10))
                .build();

        // The anti-replay rule compares real licenses only; a trial must never stand in
        // the way of the customer's first purchase.
        assertEquals(LicenseState.ACTIVE, service.install(shortLicense).state());
    }

    @Test
    @DisplayName("refuses to start without a license when the trial is disabled")
    void reportsMissingLicenseWhenTrialIsDisabled() {
        LicenseService service = service(fixedClock(LocalDate.of(2026, 6, 1)), TestLicenses.trial(false, 30));

        LicenseNotFoundException failure = assertThrows(LicenseNotFoundException.class, service::getStatus);

        // Both facts the customer needs in order to be helped without a call back.
        assertTrue(failure.getMessage().contains(licensePath.toAbsolutePath().toString()));
        assertTrue(failure.getMessage().contains(DEFAULT_FINGERPRINT));
    }

    @Test
    @DisplayName("allows writes while the license is active")
    void allowsWritesWhenActive() throws IOException {
        writeLicense(TestLicenses.license(publisherKeys).expiresOn(EXPIRY).build());
        LicenseService service = service(EXPIRY.minusDays(10));

        assertTrue(service.isWriteAllowed());
        service.assertWriteAllowed();
    }

    @Test
    @DisplayName("blocks writes but keeps reading once past expiry and grace")
    void blocksWritesWhenReadOnly() throws IOException {
        writeLicense(TestLicenses.license(publisherKeys).expiresOn(EXPIRY).graceDays(15).build());
        LicenseService service = service(EXPIRY.plusDays(16));

        assertFalse(service.isWriteAllowed());
        LicenseExpiredException failure = assertThrows(LicenseExpiredException.class, service::assertWriteAllowed);
        assertEquals(EXPIRY, failure.getExpiredOn());

        // Reading the license itself still works: the UI must be able to show the banner.
        assertEquals(LicenseState.READ_ONLY, service.getStatus().state());
        assertTrue(service.findStatus().isPresent());
    }

    @Test
    @DisplayName("re-evaluates when the day rolls over without a restart")
    void reEvaluatesOnDayChange() throws IOException {
        writeLicense(TestLicenses.license(publisherKeys).expiresOn(EXPIRY).graceDays(0).build());
        MutableClock clock = new MutableClock(EXPIRY);
        LicenseService service = service(clock);

        assertEquals(LicenseState.ACTIVE, service.getStatus().state());

        clock.setToday(EXPIRY.plusDays(1));

        assertEquals(LicenseState.READ_ONLY, service.getStatus().state(),
                "a till left running overnight must degrade on the right day");
    }

    @Test
    @DisplayName("skips every check when the module is disabled")
    void disabledModuleAllowsEverything() {
        LicenseProperties properties = new LicenseProperties(false, licensePath, null, null,
                TestLicenses.trial(true, 30));
        LicenseService service = new LicenseService(
                new LicenseVerifier(publisherKeys.getPublic()), properties,
                fixedClock(LocalDate.of(2026, 6, 1)), DEFAULT_FINGERPRINT);

        assertTrue(service.isWriteAllowed());
        service.assertWriteAllowed();
        assertFalse(service.isEnabled());
    }

    @Test
    @DisplayName("installs a renewed license atomically and keeps a backup")
    void installsRenewedLicense() throws IOException {
        writeLicense(TestLicenses.license(publisherKeys).expiresOn(EXPIRY).build());
        LicenseService service = service(EXPIRY.minusDays(20));
        assertEquals(EXPIRY, service.getStatus().license().expiresOn());

        byte[] renewed = TestLicenses.license(publisherKeys)
                .licenseId("LIC-TEST-2")
                .issuedOn(EXPIRY)
                .expiresOn(EXPIRY.plusYears(1))
                .build();

        LicenseStatus status = service.install(renewed);

        assertEquals(EXPIRY.plusYears(1), status.license().expiresOn());
        assertEquals(EXPIRY.plusYears(1), service.getStatus().license().expiresOn());
        assertTrue(Files.exists(licensePath.resolveSibling("libero-shop.lic.bak")),
                "the previous license should be kept for support");
        assertFalse(Files.exists(licensePath.resolveSibling("libero-shop.lic.tmp")),
                "the temporary file should not be left behind");
    }

    @Test
    @DisplayName("refuses a replayed licence that would move the expiry backwards")
    void refusesOlderLicense() throws IOException {
        writeLicense(TestLicenses.license(publisherKeys).expiresOn(EXPIRY).build());
        LicenseService service = service(EXPIRY.minusDays(20));

        byte[] older = TestLicenses.license(publisherKeys).expiresOn(EXPIRY.minusMonths(6)).build();

        assertThrows(LicenseInvalidException.class, () -> service.install(older));
        assertEquals(EXPIRY, service.getStatus().license().expiresOn(), "the good license must survive");
    }

    @Test
    @DisplayName("does not overwrite a working license with a forged one")
    void refusesForgedLicenseOnInstall() throws IOException {
        byte[] original = TestLicenses.license(publisherKeys).expiresOn(EXPIRY).build();
        writeLicense(original);
        LicenseService service = service(EXPIRY.minusDays(20));

        byte[] forged = TestLicenses.license(publisherKeys)
                .expiresOn(EXPIRY.plusYears(10))
                .buildSignedBy(TestLicenses.generateKeyPair().getPrivate());

        assertThrows(LicenseInvalidException.class, () -> service.install(forged));
        assertArrayEqualsOnDisk(original);
    }

    @Test
    @DisplayName("ignores a system clock wound back to dodge expiry")
    void defeatsClockRollback() throws IOException {
        writeLicense(TestLicenses.license(publisherKeys).expiresOn(EXPIRY).graceDays(0).build());

        // Day one: the application runs past expiry and records the date it has seen.
        LicenseService afterExpiry = service(EXPIRY.plusDays(30));
        assertEquals(LicenseState.READ_ONLY, afterExpiry.getStatus().state());

        // Day two: the clock is set back a year and the application restarts.
        LicenseService afterRollback = service(EXPIRY.minusYears(1));

        assertEquals(LicenseState.READ_ONLY, afterRollback.getStatus().state(),
                "winding the clock back must not resurrect an expired license");
        assertFalse(afterRollback.isWriteAllowed());
    }

    @Test
    @DisplayName("ignores a clock state file copied from another installation")
    void rejectsForeignClockState() throws IOException {
        writeLicense(TestLicenses.license(publisherKeys).expiresOn(EXPIRY).graceDays(0).build());
        // A far-future "last seen" date, hand-written with a plausible but wrong HMAC.
        Files.writeString(installDirectory.resolve(".license-state"),
                LocalDate.of(2099, 1, 1).toEpochDay() + ":00ff00ff");

        LicenseService service = service(EXPIRY.minusDays(1));

        assertEquals(LicenseState.ACTIVE, service.getStatus().state(),
                "an unauthenticated state file must be ignored, not trusted");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void writeLicense(byte[] envelope) throws IOException {
        Files.write(licensePath, envelope);
    }

    private void assertArrayEqualsOnDisk(byte[] expected) throws IOException {
        assertTrue(java.util.Arrays.equals(expected, Files.readAllBytes(licensePath)),
                "the license file on disk should not have been modified");
    }

    private LicenseService service(LocalDate today) {
        return service(fixedClock(today));
    }

    private LicenseService service(Clock clock) {
        return service(clock, TestLicenses.trial(true, 30));
    }

    private LicenseService service(Clock clock, LicenseProperties.TrialProperties trial) {
        return new LicenseService(new LicenseVerifier(publisherKeys.getPublic()),
                TestLicenses.properties(licensePath, trial), clock, DEFAULT_FINGERPRINT);
    }

    private static Clock fixedClock(LocalDate today) {
        return Clock.fixed(today.atStartOfDay(ZONE).toInstant(), ZONE);
    }

    /** A clock the test can move forward, to simulate a till left running overnight. */
    private static final class MutableClock extends Clock {

        private volatile LocalDate today;

        MutableClock(LocalDate today) {
            this.today = today;
        }

        void setToday(LocalDate value) {
            this.today = value;
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return today.atStartOfDay(ZONE).toInstant();
        }
    }
}
