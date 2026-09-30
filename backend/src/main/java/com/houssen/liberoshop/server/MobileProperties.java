package com.houssen.liberoshop.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Where the files handed to the phones live, and how phones away from the shop reach the server.
 *
 * <p>A directory beside the jar rather than inside it: a new APK is published by copying one
 * file, with no rebuild of the server and no restart, and the jar does not grow by the size of an
 * Android build. See docs/mobile-android.md.
 *
 * @param downloadsDir served as-is under {@code /downloads/}, to anyone who can reach the
 *                     server -- put nothing there that is not meant for every phone
 * @param apkFile      the name the APK is published under, fixed so that the QR code on the
 *                     download page never goes stale
 * @param publicUrl    the server's address on the Internet, e.g. {@code https://boutique.example.com},
 *                     or null when it is only reachable on the shop's network. Configured rather
 *                     than discovered: behind a box's port forwarding or a reverse proxy, the
 *                     machine has no way to know the name the outside world uses for it
 */
@ConfigurationProperties(prefix = "liberoshop.mobile")
public record MobileProperties(Path downloadsDir, String apkFile, String publicUrl) {

    public MobileProperties {
        downloadsDir = downloadsDir == null ? Path.of("downloads") : downloadsDir;
        apkFile = apkFile == null || apkFile.isBlank() ? "libero-shop.apk" : apkFile;
        // A trailing slash would double up with the "/downloads/..." path appended to it.
        publicUrl = publicUrl == null || publicUrl.isBlank() ? null : publicUrl.strip().replaceAll("/+$", "");
    }

    public Path apkPath() {
        return downloadsDir.resolve(apkFile);
    }
}
