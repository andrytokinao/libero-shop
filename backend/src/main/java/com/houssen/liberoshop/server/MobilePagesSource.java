package com.houssen.liberoshop.server;

import java.io.IOException;

/**
 * Where the server finds the mobile app's pages. Inside the jar today
 * ({@link ClasspathMobilePagesSource}); a folder beside it, or anywhere else, would be another
 * implementation -- the packaging and the update endpoint would not change.
 */
public interface MobilePagesSource {

    /** {@link MobilePages#none()} when there are none, e.g. a jar built with {@code -DskipFrontend}. */
    MobilePages read() throws IOException;
}
