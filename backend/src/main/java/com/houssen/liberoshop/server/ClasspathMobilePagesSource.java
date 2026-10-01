package com.houssen.liberoshop.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The pages the Maven build copies into {@code classpath:mobile-web/}: every jar carries the pages
 * that go with it, so deploying the jar is publishing the update.
 *
 * <p>Works the same from the IDE (a folder) and from the jar (zip entries): files are named by
 * their URL relative to the root's.
 */
@Component
public class ClasspathMobilePagesSource implements MobilePagesSource {

    static final String LOCATION = "mobile-web/";

    private final String location;

    @Autowired
    public ClasspathMobilePagesSource() {
        this(LOCATION);
    }

    /** @param location a classpath folder ending in a slash; another one in tests */
    ClasspathMobilePagesSource(String location) {
        this.location = location;
    }

    @Override
    public MobilePages read() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource root = resolver.getResource("classpath:" + location);
        if (!root.exists()) {
            return MobilePages.none();
        }
        String base = root.getURL().toString();
        SortedMap<String, byte[]> files = new TreeMap<>();
        for (Resource resource : resolver.getResources("classpath:" + location + "**/*")) {
            String url = resource.getURL().toString();
            if (!resource.isReadable() || url.endsWith("/") || !url.startsWith(base)) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                files.put(url.substring(base.length()), in.readAllBytes());
            }
        }
        return new MobilePages(files);
    }
}
