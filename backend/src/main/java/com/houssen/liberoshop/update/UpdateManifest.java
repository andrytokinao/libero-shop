package com.houssen.liberoshop.update;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * The signed description of one update: which version it brings, when it was released, and the
 * SHA-256 of the payload it must be paired with.
 *
 * <p>These are the exact bytes the publisher signed (see {@link UpdatePackage}). The hash binds
 * the signature to the payload jar: a signature over the manifest alone would let anyone swap the
 * code while keeping a genuine signature, so the manifest pins the payload by its digest and the
 * verifier refuses a payload whose digest does not match.
 *
 * @param appVersion    the version this update installs, e.g. {@code 1.2.0}
 * @param releasedOn    the day the publisher issued it
 * @param payloadSha256 lower-case hex SHA-256 of the payload entry
 * @param notes         optional release note, shown to the administrator; may be {@code null}
 */
public record UpdateManifest(String appVersion, LocalDate releasedOn, String payloadSha256, String notes) {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /**
     * Parses the signed manifest bytes. Called only once the signature over these very bytes has
     * been verified, so the content is trusted; the checks here are about shape, not authenticity.
     */
    static UpdateManifest parse(byte[] manifestBytes) {
        JsonNode node;
        try {
            node = MAPPER.readTree(new String(manifestBytes, StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new UpdateInvalidException("Le descriptif de la mise a jour est illisible.");
        }
        String appVersion = required(node, "appVersion");
        LocalDate releasedOn = date(node, "releasedOn");
        String sha = required(node, "payloadSha256").toLowerCase();
        if (!sha.matches("[0-9a-f]{64}")) {
            throw new UpdateInvalidException("L'empreinte du contenu de la mise a jour est invalide.");
        }
        String notes = node.hasNonNull("notes") ? node.path("notes").asString("") : null;
        return new UpdateManifest(appVersion, releasedOn, sha, notes);
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asString("").trim();
        if (value.isEmpty()) {
            throw new UpdateInvalidException("Champ obligatoire manquant dans la mise a jour : " + field + ".");
        }
        return value;
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = required(node, field);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new UpdateInvalidException("Date invalide dans la mise a jour (" + field + ") : " + value + ".");
        }
    }
}
