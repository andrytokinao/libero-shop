package com.houssen.liberoshop.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Turns the pages into what the phones download: the zip, its checksum (which is also its
 * version), and the oldest APK the pages run in.
 *
 * <p>That last one comes from {@code mobile-native.json}, part of the pages: a page calling a
 * plugin an older APK lacks would fail there, so such a phone is offered the new APK instead.
 */
@Component
public class MobileBundlePackager {

    static final String NATIVE_REQUIREMENTS = "mobile-native.json";

    /** When the pages do not say: any APK that can update itself can take them. */
    static final int ANY_NATIVE_BUILD = 1;

    private static final Logger log = LoggerFactory.getLogger(MobileBundlePackager.class);

    private final JsonMapper json;

    public MobileBundlePackager(JsonMapper json) {
        this.json = json;
    }

    /** @param pages complete pages: {@link MobilePages#isComplete()} */
    public MobileBundlePackage pack(MobilePages pages) {
        byte[] zip = ReproducibleZip.of(pages.files());
        String checksum = sha256(zip);
        int minNativeBuild = pages.file(NATIVE_REQUIREMENTS).map(this::minNativeBuild).orElse(ANY_NATIVE_BUILD);
        return new MobileBundlePackage(checksum.substring(0, 16), checksum, minNativeBuild, pages.count(), zip);
    }

    private int minNativeBuild(byte[] requirements) {
        try {
            JsonNode node = json.readTree(requirements).path("minNativeBuild");
            return node.isInt() ? node.asInt() : ANY_NATIVE_BUILD;
        } catch (JacksonException e) {
            log.warn("{} illisible, APK minimum suppose a {} : {}", NATIVE_REQUIREMENTS, ANY_NATIVE_BUILD, e.getMessage());
            return ANY_NATIVE_BUILD;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // Every JDK must provide SHA-256: this is a broken runtime, not a case to handle.
            throw new IllegalStateException(e);
        }
    }
}
