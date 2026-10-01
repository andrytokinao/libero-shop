package com.houssen.liberoshop.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A zip that depends on its files only, so that its checksum can be a version. */
class ReproducibleZipTest {

    private static TreeMap<String, byte[]> files() {
        TreeMap<String, byte[]> files = new TreeMap<>();
        files.put("main.js", "console.log(1)".getBytes(StandardCharsets.UTF_8));
        files.put("index.html", "<html>".getBytes(StandardCharsets.UTF_8));
        files.put("assets/logo.svg", "<svg/>".getBytes(StandardCharsets.UTF_8));
        return files;
    }

    @Test
    @DisplayName("the same files give the same bytes, whenever they are zipped")
    void sameBytes() {
        assertArrayEquals(ReproducibleZip.of(files()), ReproducibleZip.of(files()));
    }

    @Test
    @DisplayName("entries in name order, all with the same date")
    void orderAndDate() throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ReproducibleZip.of(files())))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                names.add(entry.getName());
                assertEquals(ReproducibleZip.FIXED_ENTRY_TIME, entry.getTime());
            }
        }
        assertEquals(List.of("assets/logo.svg", "index.html", "main.js"), names);
    }
}
