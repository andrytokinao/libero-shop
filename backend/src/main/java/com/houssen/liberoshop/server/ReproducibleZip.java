package com.houssen.liberoshop.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A zip whose bytes depend on its files only: entries in name order, all with the same date.
 *
 * <p>So its SHA-256 can serve as a version -- the same pages zipped twice, on two builds or two
 * servers, are the same version, and no phone downloads them again.
 */
final class ReproducibleZip {

    /** 1 January 2000: any fixed instant will do, as long as it never changes. */
    static final long FIXED_ENTRY_TIME = 946_684_800_000L;

    private ReproducibleZip() {
    }

    static byte[] of(SortedMap<String, byte[]> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(FIXED_ENTRY_TIME);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            // Writing to memory: only a JDK fault gets here.
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
