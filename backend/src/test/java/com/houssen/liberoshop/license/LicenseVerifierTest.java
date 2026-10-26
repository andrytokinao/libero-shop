package com.houssen.liberoshop.license;

import com.houssen.liberoshop.license.exception.LicenseExpiredException;
import com.houssen.liberoshop.license.exception.LicenseInvalidException;
import com.houssen.liberoshop.license.exception.LicenseMachineMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.LocalDate;

import static com.houssen.liberoshop.license.TestLicenses.DEFAULT_FINGERPRINT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseVerifierTest {

    private static final LocalDate EXPIRY = LocalDate.of(2027, 1, 1);

    private KeyPair publisherKeys;
    private LicenseVerifier verifier;

    @BeforeEach
    void setUp() {
        publisherKeys = TestLicenses.generateKeyPair();
        verifier = new LicenseVerifier(publisherKeys.getPublic());
    }

    @Nested
    @DisplayName("signature")
    class SignatureChecks {

        @Test
        @DisplayName("accepts a license signed by the publisher")
        void acceptsValidSignature() {
            byte[] envelope = TestLicenses.license(publisherKeys).expiresOn(EXPIRY).build();

            LicenseStatus status = verifier.verify(envelope, DEFAULT_FINGERPRINT, EXPIRY.minusDays(30));

            assertEquals(LicenseState.ACTIVE, status.state());
            assertEquals("CUST-0042", status.license().customerId());
            assertEquals(EXPIRY, status.license().expiresOn());
            assertTrue(status.writesAllowed());
        }

        @Test
        @DisplayName("rejects a payload edited after signing")
        void rejectsTamperedPayload() {
            // The obvious attack: open the file and push the expiry date out by ten years.
            byte[] envelope = TestLicenses.license(publisherKeys)
                    .expiresOn(EXPIRY)
                    .buildTampered("\"expiresOn\": \"2027-01-01\"", "\"expiresOn\": \"2037-01-01\"");

            LicenseInvalidException failure = assertThrows(LicenseInvalidException.class,
                    () -> verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));

            assertEquals("LICENSE_INVALID", failure.code());
            assertTrue(failure.getMessage().contains("signature"));
        }

        @Test
        @DisplayName("rejects a license signed with someone else's key")
        void rejectsForeignSignature() {
            // The other attack: generate your own key pair and sign your own license.
            KeyPair attackerKeys = TestLicenses.generateKeyPair();
            byte[] envelope = TestLicenses.license(publisherKeys)
                    .expiresOn(EXPIRY)
                    .buildSignedBy(attackerKeys.getPrivate());

            assertThrows(LicenseInvalidException.class,
                    () -> verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));
        }

        @Test
        @DisplayName("rejects an unsupported signature algorithm")
        void rejectsUnsupportedAlgorithm() {
            byte[] envelope = TestLicenses.license(publisherKeys).algorithm("NONE").build();

            assertThrows(LicenseInvalidException.class,
                    () -> verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));
        }

        @Test
        @DisplayName("rejects a file that is not a Moussa Shop license")
        void rejectsForeignFormat() {
            byte[] envelope = TestLicenses.license(publisherKeys).format("some-other-product").build();

            assertThrows(LicenseInvalidException.class,
                    () -> verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));
        }

        @Test
        @DisplayName("still accepts a licence sold under the product's former name")
        void acceptsTheLegacyFormat() {
            // A shop that paid for "liberty-shop-license" must not go read-only because the
            // product was renamed. The field is outside the signed payload, so nothing about
            // the signature check is relaxed here.
            byte[] envelope = TestLicenses.license(publisherKeys).format("liberty-shop-license").build();

            assertDoesNotThrow(
                    () -> verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));
        }

        @Test
        @DisplayName("rejects a corrupt or empty file")
        void rejectsCorruptFile() {
            assertThrows(LicenseInvalidException.class,
                    () -> verifier.verify("not json at all".getBytes(StandardCharsets.UTF_8),
                            DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));
            assertThrows(LicenseInvalidException.class,
                    () -> verifier.verify(new byte[0], DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));
        }

        @Test
        @DisplayName("rejects an envelope from a newer format version")
        void rejectsNewerVersion() {
            byte[] envelope = TestLicenses.license(publisherKeys).version(99).build();

            assertThrows(LicenseInvalidException.class,
                    () -> verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));
        }
    }

    @Nested
    @DisplayName("machine binding")
    class MachineChecks {

        @Test
        @DisplayName("rejects a license copied to another machine")
        void rejectsDifferentMachine() {
            byte[] envelope = TestLicenses.license(publisherKeys)
                    .fingerprints("LS1-AAAAA-BBBBB-CCCCC-DDDDD")
                    .build();

            LicenseMachineMismatchException failure = assertThrows(LicenseMachineMismatchException.class,
                    () -> verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));

            assertEquals(DEFAULT_FINGERPRINT, failure.getCurrentFingerprint());
            assertEquals(1, failure.getLicensedFingerprints().size());
            // The message must carry the fingerprint: it is what the customer sends to support.
            assertTrue(failure.getMessage().contains(DEFAULT_FINGERPRINT));
        }

        @Test
        @DisplayName("accepts any of the machines listed in the license")
        void acceptsAnyListedMachine() {
            byte[] envelope = TestLicenses.license(publisherKeys)
                    .fingerprints("LS1-AAAAA-BBBBB-CCCCC-DDDDD", DEFAULT_FINGERPRINT)
                    .build();

            LicenseStatus status = verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1));

            assertEquals(LicenseState.ACTIVE, status.state());
        }

        @Test
        @DisplayName("is checked after the signature, so a forged file is reported as forged")
        void signatureIsCheckedFirst() {
            // Both checks would fail; the error must name the real problem.
            byte[] envelope = TestLicenses.license(publisherKeys)
                    .fingerprints("LS1-AAAAA-BBBBB-CCCCC-DDDDD")
                    .buildSignedBy(TestLicenses.generateKeyPair().getPrivate());

            assertThrows(LicenseInvalidException.class,
                    () -> verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));
        }
    }

    @Nested
    @DisplayName("expiry and grace period")
    class ExpiryChecks {

        @Test
        @DisplayName("is active up to and including the expiry date")
        void activeUntilExpiryInclusive() {
            byte[] envelope = TestLicenses.license(publisherKeys).expiresOn(EXPIRY).graceDays(15).build();

            LicenseStatus status = verifier.verify(envelope, DEFAULT_FINGERPRINT, EXPIRY);

            assertEquals(LicenseState.ACTIVE, status.state());
            assertEquals(0, status.daysUntilExpiry());
            assertTrue(status.writesAllowed());
        }

        @Test
        @DisplayName("stays usable during the grace period, with a warning")
        void graceKeepsWritesAllowed() {
            byte[] envelope = TestLicenses.license(publisherKeys).expiresOn(EXPIRY).graceDays(15).build();

            LicenseStatus status = verifier.verify(envelope, DEFAULT_FINGERPRINT, EXPIRY.plusDays(15));

            assertEquals(LicenseState.GRACE, status.state());
            assertTrue(status.writesAllowed());
            assertEquals(1, status.daysUntilReadOnly());
            assertTrue(status.userMessage().contains("renouvellement"));
        }

        @Test
        @DisplayName("turns read-only the day after the grace period ends")
        void readOnlyAfterGrace() {
            byte[] envelope = TestLicenses.license(publisherKeys).expiresOn(EXPIRY).graceDays(15).build();

            LicenseStatus status = verifier.verify(envelope, DEFAULT_FINGERPRINT, EXPIRY.plusDays(16));

            assertEquals(LicenseState.READ_ONLY, status.state());
            assertFalse(status.writesAllowed());
            assertEquals(-16, status.daysUntilExpiry());
            assertTrue(status.userMessage().contains("lecture seule"));
        }

        @Test
        @DisplayName("turns read-only immediately when no grace was granted")
        void noGraceMeansImmediateReadOnly() {
            byte[] envelope = TestLicenses.license(publisherKeys).expiresOn(EXPIRY).graceDays(0).build();

            assertEquals(LicenseState.ACTIVE,
                    verifier.verify(envelope, DEFAULT_FINGERPRINT, EXPIRY).state());
            assertEquals(LicenseState.READ_ONLY,
                    verifier.verify(envelope, DEFAULT_FINGERPRINT, EXPIRY.plusDays(1)).state());
        }

        @Test
        @DisplayName("verifyActive refuses an expired license outright")
        void verifyActiveRejectsExpired() {
            byte[] envelope = TestLicenses.license(publisherKeys).expiresOn(EXPIRY).graceDays(0).build();

            LicenseExpiredException failure = assertThrows(LicenseExpiredException.class,
                    () -> verifier.verifyActive(envelope, DEFAULT_FINGERPRINT, EXPIRY.plusDays(1)));

            assertEquals(EXPIRY, failure.getExpiredOn());
            assertEquals("LICENSE_EXPIRED", failure.code());
        }
    }

    @Nested
    @DisplayName("payload")
    class PayloadChecks {

        @Test
        @DisplayName("preserves accented customer names through signing and parsing")
        void preservesUnicodeCustomerName() {
            // The payload is signed as bytes, so the UTF-8 round trip must be exact.
            byte[] envelope = TestLicenses.license(publisherKeys)
                    .customerName("Supermarché Houssen — Analakely")
                    .build();

            LicenseStatus status = verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1));

            assertEquals("Supermarché Houssen — Analakely", status.license().customerName());
        }

        @Test
        @DisplayName("rejects a license that lists no machine")
        void rejectsLicenseWithoutMachines() {
            byte[] envelope = TestLicenses.license(publisherKeys).fingerprints().build();

            assertThrows(LicenseInvalidException.class,
                    () -> verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));
        }

        @Test
        @DisplayName("rejects a license that expires before it was issued")
        void rejectsInvertedDates() {
            byte[] envelope = TestLicenses.license(publisherKeys)
                    .issuedOn(LocalDate.of(2027, 1, 1))
                    .expiresOn(LocalDate.of(2026, 1, 1))
                    .build();

            assertThrows(LicenseInvalidException.class,
                    () -> verifier.verify(envelope, DEFAULT_FINGERPRINT, LocalDate.of(2026, 6, 1)));
        }
    }
}
