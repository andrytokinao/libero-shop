package com.houssen.liberoshop.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pages the phones download: a zip whose checksum is its version, and says its APK floor. */
class MobileBundleTest {

    private static final String SAMPLE = "mobile-bundle-sample/";

    private MobileBundle.Packaged pack() {
        return new MobileBundle(JsonMapper.builder().build(), SAMPLE).current().orElseThrow();
    }

    @Test
    @DisplayName("zips every file with index.html at the root, as the phone expects")
    void zipLayout() throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(pack().zip()))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                names.add(entry.getName());
            }
        }
        assertEquals(List.of("assets/logo.svg", "index.html", "main-ABC.js", "mobile-native.json"), names);
    }

    @Test
    @DisplayName("the same pages give the same bytes, so a rebuild is not an update")
    void reproducible() {
        MobileBundle.Packaged first = pack();
        MobileBundle.Packaged second = pack();
        assertArrayEquals(first.zip(), second.zip());
        assertEquals(first.version(), second.version());
    }

    @Test
    @DisplayName("the checksum is the SHA-256 of the zip, and the version its first 16 digits")
    void checksum() throws NoSuchAlgorithmException {
        MobileBundle.Packaged pages = pack();
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pages.zip()));
        assertEquals(expected, pages.checksum());
        assertTrue(pages.checksum().startsWith(pages.version()));
        assertEquals(16, pages.version().length());
    }

    @Test
    @DisplayName("reads the oldest APK the pages need")
    void minNativeBuild() {
        assertEquals(3, pack().minNativeBuild());
    }

    @Test
    @DisplayName("no pages in the jar: nothing to offer, and no error")
    void absent() {
        assertTrue(new MobileBundle(JsonMapper.builder().build(), "no-such-folder/").current().isEmpty());
    }
}
