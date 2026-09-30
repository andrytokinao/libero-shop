package com.houssen.liberoshop.server;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * How the mobile app keeps its pages up to date without a new APK.
 *
 * <p>Public, like the web pages themselves: the app checks on launch, before anyone has signed
 * in, and the pages hold nothing a browser pointed at this server could not already read.
 */
@RestController
@RequestMapping("/api/mobile")
public class MobileUpdateController {

    private static final String BUNDLE_PATH = "/api/mobile/bundle/";

    private final MobileBundle bundle;

    public MobileUpdateController(MobileBundle bundle) {
        this.bundle = bundle;
    }

    /**
     * @param available      false when this jar carries no mobile pages; the app then keeps its own
     * @param url            relative to the server, so it holds whatever address the phone uses
     * @param minNativeBuild the oldest APK versionCode the pages run in
     */
    public record UpdateInfo(boolean available, String version, String checksum, String url,
                             long sizeBytes, int minNativeBuild) {
    }

    @GetMapping("/update")
    public ResponseEntity<UpdateInfo> update() {
        UpdateInfo info = bundle.current()
                .map(pages -> new UpdateInfo(true, pages.version(), pages.checksum(),
                        BUNDLE_PATH + pages.version() + ".zip", pages.zip().length, pages.minNativeBuild()))
                .orElse(new UpdateInfo(false, null, null, null, 0, 0));
        // Always asked afresh: a cached answer would hide the update it is meant to announce.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(info);
    }

    /**
     * The zip, under its version: a name that never changes content, so it may be cached for good.
     * Any other version is gone -- the phone asks {@link #update()} again and gets the current one.
     */
    @GetMapping("/bundle/{version}.zip")
    public ResponseEntity<byte[]> download(@PathVariable String version) {
        return bundle.current()
                .filter(pages -> pages.version().equals(version))
                .map(pages -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/zip"))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                        .body(pages.zip()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
