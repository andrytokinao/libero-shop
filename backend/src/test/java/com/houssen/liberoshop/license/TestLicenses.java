package com.houssen.liberoshop.license;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds signed license files for the tests.
 *
 * <p>Mirrors what {@code LicenseGeneratorCli} produces, with a throwaway key pair
 * generated per test. Keeping a copy of the envelope format here is deliberate: if the
 * production reader ever drifts from the published format, these tests fail, which is
 * exactly the alarm we want.
 */
final class TestLicenses {

    static final String DEFAULT_FINGERPRINT = "LS1-4KQ8T-9WZ2M-H7PXR-C3NVB";

    private TestLicenses() {
    }

    /**
     * Properties confined to the test's own directory.
     *
     * <p>The trial is kept deliberately local: {@code systemWide=false} means the record
     * never reaches {@code %LOCALAPPDATA%}, {@code %ProgramData%} or the registry, so a
     * test run can neither be polluted by, nor pollute, the machine it runs on.
     */
    static LicenseProperties properties(java.nio.file.Path licensePath) {
        return properties(licensePath, trial(true, 30));
    }

    static LicenseProperties properties(java.nio.file.Path licensePath,
                                        LicenseProperties.TrialProperties trial) {
        return new LicenseProperties(true, licensePath, null, null, trial);
    }

    /** A trial confined to the license directory. */
    static LicenseProperties.TrialProperties trial(boolean enabled, int days) {
        return new LicenseProperties.TrialProperties(enabled, days, null, false);
    }

    static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Builder license(KeyPair keyPair) {
        return new Builder(keyPair);
    }

    /** Fluent builder for a signed envelope. */
    static final class Builder {

        private final KeyPair keyPair;
        private String licenseId = "LIC-TEST-1";
        private String customerId = "CUST-0042";
        private String customerName = "Supermarche Houssen";
        private String plan = "ANNUAL";
        private LocalDate issuedOn = LocalDate.of(2026, 1, 1);
        private LocalDate expiresOn = LocalDate.of(2027, 1, 1);
        private int graceDays = 15;
        private List<String> fingerprints = List.of(DEFAULT_FINGERPRINT);
        private String algorithm = "Ed25519";
        private String format = LicenseFile.FORMAT;
        private int version = LicenseFile.SUPPORTED_VERSION;

        private Builder(KeyPair keyPair) {
            this.keyPair = keyPair;
        }

        Builder licenseId(String value) {
            this.licenseId = value;
            return this;
        }

        Builder customerName(String value) {
            this.customerName = value;
            return this;
        }

        Builder issuedOn(LocalDate value) {
            this.issuedOn = value;
            return this;
        }

        Builder expiresOn(LocalDate value) {
            this.expiresOn = value;
            return this;
        }

        Builder graceDays(int value) {
            this.graceDays = value;
            return this;
        }

        Builder fingerprints(String... values) {
            this.fingerprints = List.of(values);
            return this;
        }

        Builder algorithm(String value) {
            this.algorithm = value;
            return this;
        }

        Builder format(String value) {
            this.format = value;
            return this;
        }

        Builder version(int value) {
            this.version = value;
            return this;
        }

        /** The payload JSON, before encoding and signing. */
        String payloadJson() {
            String machines = fingerprints.stream()
                    .map(value -> '"' + value + '"')
                    .collect(Collectors.joining(", "));
            return """
                    {
                      "licenseId": "%s",
                      "customerId": "%s",
                      "customerName": "%s",
                      "plan": "%s",
                      "issuedOn": "%s",
                      "expiresOn": "%s",
                      "graceDays": %d,
                      "machineFingerprints": [%s]
                    }""".formatted(licenseId, customerId, customerName, plan, issuedOn, expiresOn,
                    graceDays, machines);
        }

        /** A correctly signed envelope. */
        byte[] build() {
            byte[] payload = payloadJson().getBytes(StandardCharsets.UTF_8);
            return envelope(payload, sign(keyPair.getPrivate(), payload));
        }

        /**
         * An envelope whose payload was modified after signing -- what an attacker gets by
         * editing the expiry date in a text editor and re-encoding it.
         */
        byte[] buildTampered(String search, String replacement) {
            byte[] originalPayload = payloadJson().getBytes(StandardCharsets.UTF_8);
            byte[] signature = sign(keyPair.getPrivate(), originalPayload);
            byte[] tampered = payloadJson().replace(search, replacement).getBytes(StandardCharsets.UTF_8);
            return envelope(tampered, signature);
        }

        /** An envelope signed with a different private key. */
        byte[] buildSignedBy(PrivateKey otherKey) {
            byte[] payload = payloadJson().getBytes(StandardCharsets.UTF_8);
            return envelope(payload, sign(otherKey, payload));
        }

        private byte[] envelope(byte[] payload, byte[] signature) {
            String json = """
                    {
                      "format": "%s",
                      "version": %d,
                      "algorithm": "%s",
                      "payload": "%s",
                      "signature": "%s"
                    }""".formatted(
                    format,
                    version,
                    algorithm,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(payload),
                    Base64.getEncoder().encodeToString(signature));
            return json.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static byte[] sign(PrivateKey key, byte[] payload) {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(key);
            signer.update(payload);
            return signer.sign();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
