package com.houssen.liberoshop.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The key an installation draws for itself: the same one after a restart, so nobody at the
 * till is signed out because the computer rebooted.
 */
class JwtSecretFileTest {

    private static final String ISSUER = "libero-shop";

    private final JwtConfiguration configuration = new JwtConfiguration();

    @TempDir
    Path folder;

    @Test
    @DisplayName("Le premier demarrage genere la cle et l'ecrit, dossier compris")
    void createsTheKeyAtFirstStart() throws IOException {
        Path file = folder.resolve("security").resolve("jwt-secret.key");

        byte[] key = JwtSecretFile.loadOrCreate(file);

        assertTrue(key.length >= JwtProperties.MIN_SECRET_BYTES);
        assertArrayEquals(key, Files.readAllBytes(file));
        assertEquals(1, Files.list(file.getParent()).count(), "aucun brouillon ne reste a cote");
    }

    @Test
    @DisplayName("Un redemarrage relit la meme cle")
    void readsTheSameKeyBack() throws IOException {
        Path file = folder.resolve("jwt-secret.key");

        byte[] first = JwtSecretFile.loadOrCreate(file);
        byte[] second = JwtSecretFile.loadOrCreate(file);

        assertArrayEquals(first, second);
    }

    @Test
    @DisplayName("Deux installations ne partagent pas leur cle")
    void twoInstallationsDiffer() throws IOException {
        byte[] one = JwtSecretFile.loadOrCreate(folder.resolve("a.key"));
        byte[] other = JwtSecretFile.loadOrCreate(folder.resolve("b.key"));

        assertNotEquals(new String(one), new String(other));
    }

    @Test
    @DisplayName("Un fichier tronque est remplace plutot que cru")
    void replacesATruncatedFile() throws IOException {
        Path file = folder.resolve("jwt-secret.key");
        Files.writeString(file, "trop-court");

        byte[] key = JwtSecretFile.loadOrCreate(file);

        assertTrue(key.length >= JwtProperties.MIN_SECRET_BYTES);
        assertArrayEquals(key, Files.readAllBytes(file));
    }

    @Test
    @DisplayName("La configuration signe avec la cle du fichier, la meme a chaque demarrage")
    void theConfigurationUsesTheFile() {
        JwtProperties properties = new JwtProperties("", folder.resolve("jwt-secret.key").toString(),
                Duration.ofHours(12), ISSUER);
        assertTrue(properties.keepsItsKeyInAFile());

        SecretKey first = configuration.jwtSigningKey(properties);
        SecretKey second = configuration.jwtSigningKey(properties);

        assertArrayEquals(first.getEncoded(), second.getEncoded());
    }

    @Test
    @DisplayName("Un secret ecrit dans la configuration passe avant le fichier, qui n'est pas cree")
    void aConfiguredSecretWins() {
        Path file = folder.resolve("jwt-secret.key");
        String secret = "un-secret-de-configuration-assez-long-pour-hs256";
        JwtProperties properties = new JwtProperties(secret, file.toString(), Duration.ofHours(12), ISSUER);
        assertFalse(properties.keepsItsKeyInAFile());

        SecretKey key = configuration.jwtSigningKey(properties);

        assertArrayEquals(secret.getBytes(), key.getEncoded());
        assertFalse(Files.exists(file));
    }

    @Test
    @DisplayName("Un emplacement inutilisable n'empeche pas de demarrer : cle du jour")
    void anUnusableLocationStillStarts() throws IOException {
        // A file where a folder is needed: nothing can be written under it.
        Path blocker = Files.writeString(folder.resolve("occupe"), "x");
        JwtProperties properties = new JwtProperties("", blocker.resolve("jwt-secret.key").toString(),
                Duration.ofHours(12), ISSUER);

        SecretKey first = configuration.jwtSigningKey(properties);
        SecretKey second = configuration.jwtSigningKey(properties);

        assertEquals(JwtProperties.MIN_SECRET_BYTES, first.getEncoded().length);
        assertNotEquals(java.util.HexFormat.of().formatHex(first.getEncoded()),
                java.util.HexFormat.of().formatHex(second.getEncoded()));
    }
}
