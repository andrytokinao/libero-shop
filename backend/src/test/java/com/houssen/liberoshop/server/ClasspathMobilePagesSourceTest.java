package com.houssen.liberoshop.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pages the build put on the classpath, named relative to their root. */
class ClasspathMobilePagesSourceTest {

    @Test
    @DisplayName("reads every file, sub-folders included, by its path under the root")
    void readsFolder() throws IOException {
        MobilePages pages = new ClasspathMobilePagesSource("mobile-bundle-sample/").read();

        assertEquals(List.of("assets/logo.svg", "index.html", "main-ABC.js", "mobile-native.json"),
                List.copyOf(pages.files().keySet()));
        assertTrue(pages.isComplete());
    }

    @Test
    @DisplayName("no folder, no pages -- and no error")
    void absent() throws IOException {
        MobilePages pages = new ClasspathMobilePagesSource("no-such-folder/").read();

        assertEquals(0, pages.count());
        assertFalse(pages.isComplete());
    }
}
