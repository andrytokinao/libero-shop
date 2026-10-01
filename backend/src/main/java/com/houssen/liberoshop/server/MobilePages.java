package com.houssen.liberoshop.server;

import java.util.Collections;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The files of the mobile app's pages, by path relative to their root, in name order.
 *
 * @param files never null; sorted, so that whatever is built from them comes out the same twice
 */
public record MobilePages(SortedMap<String, byte[]> files) {

    /** The page the app opens: without it, there are no pages to speak of. */
    public static final String ENTRY_PAGE = "index.html";

    public MobilePages {
        files = Collections.unmodifiableSortedMap(new TreeMap<>(files));
    }

    public static MobilePages none() {
        return new MobilePages(new TreeMap<>());
    }

    public boolean isComplete() {
        return files.containsKey(ENTRY_PAGE);
    }

    public Optional<byte[]> file(String path) {
        return Optional.ofNullable(files.get(path));
    }

    public int count() {
        return files.size();
    }
}
