package com.houssen.liberoshop.license;

import com.houssen.liberoshop.license.exception.LicenseInvalidException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Reads the {@code .lic} envelope produced by {@code LicenseGeneratorCli}.
 *
 * <p>The envelope keeps the signed payload as an opaque Base64URL blob:
 * <pre>
 * {
 *   "format": "libero-shop-license",
 *   "version": 1,
 *   "algorithm": "Ed25519",
 *   "payload": "eyJsaWNlbnNlSWQiOi...",
 *   "signature": "9Qm1f..."
 * }
 * </pre>
 *
 * <p>That indirection matters: the signature covers the <em>exact</em> payload bytes, so
 * verification never depends on how this JVM happens to re-serialize a JSON object. Key
 * ordering, whitespace, number formatting and Unicode escaping become irrelevant, and
 * with them a whole family of "valid license rejected on customer machine" bugs.
 *
 * @param payloadBytes the signed bytes, verbatim
 * @param signature    the detached signature over {@code payloadBytes}
 * @param algorithm    the signature algorithm named by the envelope
 */
public record LicenseFile(byte[] payloadBytes, byte[] signature, String algorithm) {

    /** Envelope discriminator; must match what the generator writes. */
    static final String FORMAT = "libero-shop-license";

    /**
     * What the discriminator said while the product was called Liberty Shop.
     *
     * <p>Still accepted, and it costs nothing to: the field sits in the envelope, outside the
     * signed payload, so honouring the old spelling weakens no check -- the signature is
     * verified exactly as before. Refusing it would turn every licence sold under the former
     * name into a read-only installation on the day the customer upgrades.
     */
    private static final String LEGACY_FORMAT = "liberty-shop-license";

    /** Highest envelope version this build understands. */
    static final int SUPPORTED_VERSION = 1;

    /** Shared, thread-safe and immutable; no license-specific configuration needed. */
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /**
     * Parses an envelope. Structural problems are reported as
     * {@link LicenseInvalidException}: from the customer's point of view a corrupt file
     * and a forged one need the same remedy, a fresh file from the publisher.
     */
    public static LicenseFile parse(byte[] rawEnvelope) {
        if (rawEnvelope == null || rawEnvelope.length == 0) {
            throw new LicenseInvalidException("Le fichier de licence est vide.");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(new String(rawEnvelope, StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new LicenseInvalidException("Le fichier de licence n'est pas un fichier JSON valide.", e);
        }
        String format = text(root, "format");
        if (!FORMAT.equals(format) && !LEGACY_FORMAT.equals(format)) {
            throw new LicenseInvalidException("Ce fichier n'est pas une licence Libero Shop.");
        }
        int version = root.path("version").asInt(0);
        if (version > SUPPORTED_VERSION) {
            throw new LicenseInvalidException("Ce fichier de licence (version " + version
                    + ") requiert une version plus recente de l'application.");
        }
        String algorithm = text(root, "algorithm");
        byte[] payload = decode(text(root, "payload"), Base64.getUrlDecoder(), "payload");
        byte[] signature = decode(text(root, "signature"), Base64.getDecoder(), "signature");
        return new LicenseFile(payload, signature, algorithm);
    }

    /**
     * Turns the signed payload into the domain model.
     *
     * <p>Only called once the signature has been verified, so the content is trusted;
     * the checks here are about shape, not authenticity.
     */
    License toLicense() {
        JsonNode payload;
        try {
            payload = MAPPER.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new LicenseInvalidException("Le contenu de la licence est illisible.", e);
        }
        List<String> fingerprints = new ArrayList<>();
        for (JsonNode node : payload.path("machineFingerprints")) {
            String fingerprint = node.asString("").trim();
            if (!fingerprint.isEmpty()) {
                fingerprints.add(fingerprint);
            }
        }
        if (fingerprints.isEmpty()) {
            throw new LicenseInvalidException("La licence ne designe aucune machine autorisee.");
        }
        LocalDate issuedOn = date(payload, "issuedOn");
        LocalDate expiresOn = date(payload, "expiresOn");
        if (expiresOn.isBefore(issuedOn)) {
            throw new LicenseInvalidException("La licence expire avant sa date d'emission.");
        }
        int graceDays = Math.max(0, payload.path("graceDays").asInt(0));
        String notes = payload.hasNonNull("notes") ? payload.path("notes").asString("") : null;
        return new License(
                required(payload, "licenseId"),
                required(payload, "customerId"),
                required(payload, "customerName"),
                LicensePlan.from(payload.path("plan").asString("")),
                issuedOn,
                expiresOn,
                graceDays,
                fingerprints,
                notes);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("").trim();
    }

    private static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isEmpty()) {
            throw new LicenseInvalidException("Champ obligatoire manquant dans la licence : " + field + ".");
        }
        return value;
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = required(node, field);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new LicenseInvalidException("Date invalide dans la licence (" + field + ") : " + value + ".", e);
        }
    }

    private static byte[] decode(String value, Base64.Decoder decoder, String field) {
        if (value.isEmpty()) {
            throw new LicenseInvalidException("Le fichier de licence ne contient pas de champ '" + field + "'.");
        }
        try {
            return decoder.decode(value);
        } catch (IllegalArgumentException e) {
            throw new LicenseInvalidException("Le champ '" + field + "' du fichier de licence est corrompu.", e);
        }
    }
}
