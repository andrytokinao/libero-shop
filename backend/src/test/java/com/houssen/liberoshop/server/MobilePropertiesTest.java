package com.houssen.liberoshop.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which APK the phones get: the copy in the downloads folder, or the installer's. */
class MobilePropertiesTest {

    private static final Instant BUILT = Instant.parse("2026-10-10T10:00:00Z");

    @TempDir
    Path folder;

    @Test
    @DisplayName("Sans aucune APK, rien a telecharger")
    void nothingPublished() {
        assertEquals(Optional.empty(), properties(folder.resolve("libero-shop.apk")).publishedApk());
        assertEquals(Optional.empty(), properties(null).publishedApk());
    }

    @Test
    @DisplayName("Installation neuve : l'APK livree avec l'installateur")
    void theInstallersCopyOnAFreshInstallation() throws IOException {
        Path bundled = apk("app", BUILT);

        assertEquals(Optional.of(bundled), properties(bundled).publishedApk());
    }

    @Test
    @DisplayName("Une APK plus recente copiee dans le dossier passe devant celle de l'installateur")
    void aNewerCopyInTheFolderWins() throws IOException {
        Path bundled = apk("app", BUILT);
        Path copied = apk("downloads", BUILT.plusSeconds(3600));

        assertEquals(Optional.of(copied), properties(bundled).publishedApk());
    }

    @Test
    @DisplayName("Un nouvel installateur passe devant une ancienne APK restee dans le dossier")
    void aNewerInstallerWins() throws IOException {
        Path copied = apk("downloads", BUILT);
        Path bundled = apk("app", BUILT.plusSeconds(3600));

        assertEquals(Optional.of(bundled), properties(bundled).publishedApk());
        assertEquals(Optional.of(copied), properties(null).publishedApk());
    }

    private MobileProperties properties(Path bundled) {
        return new MobileProperties(folder.resolve("downloads"), "libero-shop.apk", bundled, null);
    }

    private Path apk(String directory, Instant builtAt) throws IOException {
        Path file = Files.createDirectories(folder.resolve(directory)).resolve("libero-shop.apk");
        Files.writeString(file, directory);
        Files.setLastModifiedTime(file, FileTime.from(builtAt));
        return file;
    }
}
