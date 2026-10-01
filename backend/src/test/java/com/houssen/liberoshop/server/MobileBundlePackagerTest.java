package com.houssen.liberoshop.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the phone is told about the pages: their version, their checksum, the APK they need. */
class MobileBundlePackagerTest {

    private final MobileBundlePackager packager = new MobileBundlePackager(JsonMapper.builder().build());

    private static MobilePages pages(String nativeRequirements) {
        TreeMap<String, byte[]> files = new TreeMap<>();
        files.put("index.html", "<html>".getBytes(StandardCharsets.UTF_8));
        if (nativeRequirements != null) {
            files.put(MobileBundlePackager.NATIVE_REQUIREMENTS, nativeRequirements.getBytes(StandardCharsets.UTF_8));
        }
        return new MobilePages(files);
    }

    @Test
    @DisplayName("the checksum is the zip's SHA-256, and the version its first 16 digits")
    void checksumAndVersion() throws NoSuchAlgorithmException {
        MobileBundlePackage bundle = packager.pack(pages(null));

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bundle.zip()));
        assertEquals(expected, bundle.checksum());
        assertEquals(16, bundle.version().length());
        assertTrue(bundle.checksum().startsWith(bundle.version()));
        assertEquals(1, bundle.fileCount());
    }

    @Test
    @DisplayName("reads the oldest APK the pages need")
    void minNativeBuild() {
        assertEquals(3, packager.pack(pages("{\"minNativeBuild\": 3}")).minNativeBuild());
    }

    @Test
    @DisplayName("pages that do not say, or say it badly, run in any APK that updates itself")
    void minNativeBuildDefault() {
        assertEquals(MobileBundlePackager.ANY_NATIVE_BUILD, packager.pack(pages(null)).minNativeBuild());
        assertEquals(MobileBundlePackager.ANY_NATIVE_BUILD, packager.pack(pages("pas du json")).minNativeBuild());
        assertEquals(MobileBundlePackager.ANY_NATIVE_BUILD, packager.pack(pages("{\"minNativeBuild\": \"2\"}")).minNativeBuild());
    }
}
