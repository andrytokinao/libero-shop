package com.houssen.liberoshop.update;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * What the update URL returns: a small JSON pointing at the latest {@code .lsu}.
 *
 * <pre>
 * {
 *   "latestVersion": "1.3.0",
 *   "releasedOn": "2026-10-15",
 *   "notes": "Corrections diverses.",
 *   "downloadUrl": "https://.../libero-shop-1.3.0.lsu"
 * }
 * </pre>
 *
 * <p>This manifest is a hint, not an authority: the {@code .lsu} it points at is signature-checked
 * on arrival, so a wrong or tampered manifest can at worst send the server to download a file that
 * is then rejected -- it can never make it run unsigned code.
 *
 * @param latestVersion the newest published version
 * @param releasedOn    the day it was released, {@code yyyy-MM-dd}; may be {@code null}
 * @param notes         release note shown to the administrator; may be {@code null}
 * @param downloadUrl   absolute http/https URL of the {@code .lsu}
 */
public record RemoteUpdateInfo(String latestVersion, String releasedOn, String notes, String downloadUrl) {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    static RemoteUpdateInfo parse(byte[] json) {
        JsonNode node;
        try {
            node = MAPPER.readTree(new String(json, StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new UpdateInvalidException("La reponse du serveur de mise a jour n'est pas lisible.");
        }
        String latest = node.path("latestVersion").asString("").trim();
        String downloadUrl = node.path("downloadUrl").asString("").trim();
        if (latest.isEmpty() || downloadUrl.isEmpty()) {
            throw new UpdateInvalidException("La reponse du serveur de mise a jour est incomplete.");
        }
        if (!downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://")) {
            throw new UpdateInvalidException("L'adresse de telechargement de la mise a jour est invalide.");
        }
        String releasedOn = node.hasNonNull("releasedOn") ? node.path("releasedOn").asString("") : null;
        String notes = node.hasNonNull("notes") ? node.path("notes").asString("") : null;
        return new RemoteUpdateInfo(latest, releasedOn, notes, downloadUrl);
    }
}
