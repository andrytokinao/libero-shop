package com.houssen.liberoshop.update;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds signed {@code .lsu} fixtures for the update tests, mirroring what the publisher's signer
 * produces. The same shape as {@code TestLicenses}: a throwaway Ed25519 key signs the exact
 * manifest bytes, and the envelope keeps that signed blob opaque.
 */
final class TestUpdatePackages {

    private TestUpdatePackages() {
    }

    static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Builder update(KeyPair keyPair) {
        return new Builder(keyPair);
    }

    static final class Builder {
        private final KeyPair keyPair;
        private String appVersion = "1.2.0";
        private String releasedOn = "2026-10-15";
        private String notes = "Corrections diverses.";
        private byte[] payload = "nouveau programme".getBytes(StandardCharsets.UTF_8);
        private String algorithm = "Ed25519";
        private boolean tamperPayload;
        private boolean tamperSignature;

        private Builder(KeyPair keyPair) {
            this.keyPair = keyPair;
        }

        Builder version(String v) {
            this.appVersion = v;
            return this;
        }

        Builder releasedOn(String date) {
            this.releasedOn = date;
            return this;
        }

        Builder notes(String n) {
            this.notes = n;
            return this;
        }

        Builder payload(byte[] bytes) {
            this.payload = bytes;
            return this;
        }

        Builder algorithm(String a) {
            this.algorithm = a;
            return this;
        }

        /** Writes a payload whose bytes no longer match the signed digest. */
        Builder withPayloadTamperedAfterSigning() {
            this.tamperPayload = true;
            return this;
        }

        /** Corrupts the signature so verification must fail. */
        Builder withBrokenSignature() {
            this.tamperSignature = true;
            return this;
        }

        Path writeTo(Path file) {
            try {
                String sha = sha256Hex(payload);
                String manifestJson = "{"
                        + "\"appVersion\":\"" + appVersion + "\","
                        + "\"releasedOn\":\"" + releasedOn + "\","
                        + "\"payloadSha256\":\"" + sha + "\""
                        + (notes == null ? "" : ",\"notes\":\"" + notes + "\"")
                        + "}";
                byte[] manifestBytes = manifestJson.getBytes(StandardCharsets.UTF_8);
                byte[] signature = sign(keyPair.getPrivate(), manifestBytes);
                if (tamperSignature) {
                    signature[0] ^= 0x01;
                }
                String envelopeJson = "{"
                        + "\"format\":\"libero-shop-update\","
                        + "\"version\":1,"
                        + "\"algorithm\":\"" + algorithm + "\","
                        + "\"manifest\":\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(manifestBytes) + "\","
                        + "\"signature\":\"" + Base64.getEncoder().encodeToString(signature) + "\""
                        + "}";
                byte[] payloadToWrite = tamperPayload
                        ? "programme altere apres signature".getBytes(StandardCharsets.UTF_8)
                        : payload;
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
                    writeEntry(zip, "envelope.json", envelopeJson.getBytes(StandardCharsets.UTF_8));
                    writeEntry(zip, "payload.bin", payloadToWrite);
                }
                return file;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        OutputStream out = zip;
        out.write(bytes);
        zip.closeEntry();
    }

    private static byte[] sign(PrivateKey key, byte[] bytes) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key);
        signer.update(bytes);
        return signer.sign();
    }

    static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
