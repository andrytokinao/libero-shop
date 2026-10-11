package com.houssen.liberoshop.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

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
 * @param bundledApk   the APK delivered with the installer, inside the application's own folder,
 *                     or null when the server was not installed (IDE, {@code java -jar}). It is
 *                     what makes the download work on a fresh installation, before anyone has
 *                     copied anything anywhere
 * @param publicUrl    the server's address on the Internet, e.g. {@code https://boutique.example.com},
 *                     or null when it is only reachable on the shop's network. Configured rather
 *                     than discovered: behind a box's port forwarding or a reverse proxy, the
 *                     machine has no way to know the name the outside world uses for it
 */
@ConfigurationProperties(prefix = "liberoshop.mobile")
public record MobileProperties(Path downloadsDir, String apkFile, Path bundledApk, String publicUrl) {

    public MobileProperties {
        downloadsDir = downloadsDir == null ? Path.of("downloads") : downloadsDir;
        bundledApk = bundledApk == null || bundledApk.toString().isBlank() ? null : bundledApk;
        apkFile = apkFile == null || apkFile.isBlank() ? "libero-shop.apk" : apkFile;
        // A trailing slash would double up with the "/downloads/..." path appended to it.
        publicUrl = publicUrl == null || publicUrl.isBlank() ? null : publicUrl.strip().replaceAll("/+$", "");
    }

    public Path apkPath() {
        return downloadsDir.resolve(apkFile);
    }

    /**
     * The APK the phones get: the one copied into the downloads folder or the one delivered with
     * the installer, whichever was built last. Empty when there is neither.
     *
     * <p>The newer of the two, not a fixed order: an APK copied by hand is how a new version is
     * published between two installers, and an installer is how it reaches a shop where an older
     * copy was left in the folder -- either way the stale one must not win.
     */
    public Optional<Path> publishedApk() {
        return Stream.of(apkPath(), bundledApk)
                .filter(file -> file != null && Files.isRegularFile(file))
                .max(Comparator.comparing(MobileProperties::lastModified));
    }

    private static FileTime lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }
}
