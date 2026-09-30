package com.houssen.liberoshop.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The mobile app's pages, as the phones download them to update themselves.
 *
 * <p>The Maven build compiles the Angular app a second time, in its mobile configuration, into
 * {@code classpath:mobile-web/}: every jar carries the pages that go with it. They are zipped here,
 * once, on first request -- no zip tool in the build, and nothing to publish by hand: deploying the
 * jar is publishing the update.
 *
 * <p>The zip is reproducible: entries in name order, with a fixed date. So its SHA-256 depends only
 * on the pages, and serves both as the checksum the phone verifies and as the version it compares
 * -- a rebuild of the same pages is not an update, and no phone downloads it twice.
 *
 * <p>{@code mobile-native.json}, part of the pages, says the oldest APK these pages can run in: a
 * page calling a plugin that an older APK lacks would fail, so such a phone is asked to install
 * the new APK instead of receiving the pages.
 */
@Component
public class MobileBundle {

    static final String LOCATION = "mobile-web/";
    static final String NATIVE_REQUIREMENTS = "mobile-native.json";

    /** 1 January 2000: any fixed instant makes the zip byte-for-byte reproducible. */
    private static final long FIXED_ENTRY_TIME = 946_684_800_000L;

    private static final Logger log = LoggerFactory.getLogger(MobileBundle.class);

    private final JsonMapper json;
    private final String location;
    private volatile Optional<Packaged> packaged;

    @Autowired
    public MobileBundle(JsonMapper json) {
        this(json, LOCATION);
    }

    /** @param location a classpath folder ending in a slash; another one in tests */
    MobileBundle(JsonMapper json, String location) {
        this.json = json;
        this.location = location;
    }

    /**
     * @param version       the first 16 hex digits of the checksum: short enough to read in a log
     * @param checksum      SHA-256 of the zip, hex
     * @param minNativeBuild the oldest APK {@code versionCode} these pages run in
     */
    public record Packaged(String version, String checksum, int minNativeBuild, byte[] zip) {
    }

    /** Empty when the jar was built without the mobile pages, e.g. {@code -DskipFrontend}. */
    public Optional<Packaged> current() {
        Optional<Packaged> result = packaged;
        if (result == null) {
            synchronized (this) {
                result = packaged;
                if (result == null) {
                    result = pack();
                    packaged = result;
                }
            }
        }
        return result;
    }

    private Optional<Packaged> pack() {
        try {
            Map<String, byte[]> files = readFiles();
            if (!files.containsKey("index.html")) {
                log.info("Aucune page mobile dans ce jar : mise a jour automatique de l'application desactivee.");
                return Optional.empty();
            }
            byte[] zip = zip(files);
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip));
            Packaged result = new Packaged(checksum.substring(0, 16), checksum,
                    minNativeBuild(files.get(NATIVE_REQUIREMENTS)), zip);
            log.info("Pages mobiles {} pretes pour la mise a jour des telephones ({} fichiers, {} Ko, APK {} minimum).",
                    result.version(), files.size(), zip.length / 1024, result.minNativeBuild());
            return Optional.of(result);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warn("Pages mobiles illisibles : mise a jour automatique desactivee. {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Every file under the location, by path relative to it; sorted, for a reproducible zip. */
    private Map<String, byte[]> readFiles() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource root = resolver.getResource("classpath:" + location);
        Map<String, byte[]> files = new TreeMap<>();
        if (!root.exists()) {
            return files;
        }
        String base = root.getURL().toString();
        for (Resource resource : resolver.getResources("classpath:" + location + "**/*")) {
            String url = resource.getURL().toString();
            if (!resource.isReadable() || url.endsWith("/") || !url.startsWith(base)) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                files.put(url.substring(base.length()), in.readAllBytes());
            }
        }
        return files;
    }

    private static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(FIXED_ENTRY_TIME);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** 1 when the file is absent or unreadable: every APK with the updater can take the pages. */
    private int minNativeBuild(byte[] requirements) {
        if (requirements == null) {
            return 1;
        }
        try {
            JsonNode node = json.readTree(requirements).path("minNativeBuild");
            return node.isInt() ? node.asInt() : 1;
        } catch (JacksonException e) {
            log.warn("{} illisible, APK minimum suppose a 1 : {}", NATIVE_REQUIREMENTS, e.getMessage());
            return 1;
        }
    }
}
