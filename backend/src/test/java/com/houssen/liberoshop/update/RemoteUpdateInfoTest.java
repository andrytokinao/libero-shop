package com.houssen.liberoshop.update;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteUpdateInfoTest {

    @Test
    void parsesAFullManifest() {
        RemoteUpdateInfo info = RemoteUpdateInfo.parse(("""
                {"latestVersion":"1.3.0","releasedOn":"2026-10-15",
                 "notes":"Corrections.","downloadUrl":"https://example.com/u.lsu"}
                """).getBytes(StandardCharsets.UTF_8));

        assertThat(info.latestVersion()).isEqualTo("1.3.0");
        assertThat(info.downloadUrl()).isEqualTo("https://example.com/u.lsu");
        assertThat(info.notes()).isEqualTo("Corrections.");
    }

    @Test
    void rejectsAManifestWithoutAVersionOrUrl() {
        assertThatThrownBy(() -> RemoteUpdateInfo.parse("{\"latestVersion\":\"1.3.0\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UpdateInvalidException.class);
    }

    @Test
    void rejectsANonHttpDownloadUrl() {
        assertThatThrownBy(() -> RemoteUpdateInfo.parse(
                "{\"latestVersion\":\"1.3.0\",\"downloadUrl\":\"file:///etc/passwd\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UpdateInvalidException.class);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> RemoteUpdateInfo.parse("pas du json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UpdateInvalidException.class);
    }
}
