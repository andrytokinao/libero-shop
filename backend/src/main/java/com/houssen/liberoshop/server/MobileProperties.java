package com.houssen.liberoshop.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Where the files handed to the phones live.
 *
 * <p>A directory beside the jar rather than inside it: a new APK is published by copying one
 * file, with no rebuild of the server and no restart, and the jar does not grow by the size of an
 * Android build. See docs/mobile-android.md.
 *
 * @param downloadsDir served as-is under {@code /downloads/}, to anyone who can reach the
 *                     server -- put nothing there that is not meant for every phone
 * @param apkFile      the name the APK is published under, fixed so that the QR code on the
 *                     administrator's screen never goes stale
 */
@ConfigurationProperties(prefix = "liberoshop.mobile")
public record MobileProperties(Path downloadsDir, String apkFile) {

    public MobileProperties {
        downloadsDir = downloadsDir == null ? Path.of("downloads") : downloadsDir;
        apkFile = apkFile == null || apkFile.isBlank() ? "libero-shop.apk" : apkFile;
    }

    public Path apkPath() {
        return downloadsDir.resolve(apkFile);
    }
}
