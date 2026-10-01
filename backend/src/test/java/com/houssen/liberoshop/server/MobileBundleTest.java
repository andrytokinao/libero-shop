package com.houssen.liberoshop.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the pages are read and packed, and what is offered when there are none to offer. */
class MobileBundleTest {

    private final MobileBundlePackager packager = new MobileBundlePackager(JsonMapper.builder().build());

    private static MobilePages complete() {
        TreeMap<String, byte[]> files = new TreeMap<>();
        files.put(MobilePages.ENTRY_PAGE, "<html>".getBytes(StandardCharsets.UTF_8));
        return new MobilePages(files);
    }

    @Test
    @DisplayName("reads and packs once, then keeps the result")
    void packsOnce() {
        AtomicInteger reads = new AtomicInteger();
        MobileBundle bundle = new MobileBundle(() -> {
            reads.incrementAndGet();
            return complete();
        }, packager);

        MobileBundlePackage first = bundle.current().orElseThrow();
        MobileBundlePackage second = bundle.current().orElseThrow();

        assertSame(first, second);
        assertEquals(1, reads.get());
    }

    @Test
    @DisplayName("pages without their entry page are no pages: nothing is offered")
    void incomplete() {
        assertTrue(new MobileBundle(MobilePages::none, packager).current().isEmpty());
    }

    @Test
    @DisplayName("pages that cannot be read: nothing is offered, and the server keeps running")
    void unreadable() {
        MobileBundle bundle = new MobileBundle(() -> {
            throw new IOException("disque illisible");
        }, packager);

        assertTrue(bundle.current().isEmpty());
    }
}
