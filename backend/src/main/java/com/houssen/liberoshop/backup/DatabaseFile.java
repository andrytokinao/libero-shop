package com.houssen.liberoshop.backup;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Where the shop's H2 database lives on disk, read from the datasource URL.
 *
 * <p>Only a file database has one: {@code jdbc:h2:mem:} (the tests) and {@code jdbc:h2:tcp:} (a
 * database served by another process) have nothing here to copy, and backups are then simply
 * unavailable rather than failing every hour.
 *
 * @param base the path without H2's {@code .mv.db} suffix, as the URL gives it
 */
public record DatabaseFile(Path base) {

    static final String SUFFIX = ".mv.db";
    private static final String PREFIX = "jdbc:h2:";

    /** {@code jdbc:h2:file:./data/libertyshop;MODE=...} or {@code jdbc:h2:./data/libertyshop}. */
    public static Optional<DatabaseFile> fromUrl(String url) {
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = url.substring(PREFIX.length());
        int options = rest.indexOf(';');
        if (options >= 0) {
            rest = rest.substring(0, options);
        }
        String lower = rest.toLowerCase(Locale.ROOT);
        if (lower.startsWith("mem:") || lower.startsWith("tcp:") || lower.startsWith("ssl:")
                || lower.startsWith("zip:")) {
            return Optional.empty();
        }
        if (lower.startsWith("file:")) {
            rest = rest.substring("file:".length());
        }
        if (rest.startsWith("~")) {
            rest = System.getProperty("user.home") + rest.substring(1);
        }
        return rest.isBlank() ? Optional.empty() : Optional.of(new DatabaseFile(Path.of(rest).toAbsolutePath().normalize()));
    }

    /** The file H2 writes: {@code libertyshop.mv.db}. */
    public Path file() {
        return base.resolveSibling(name() + SUFFIX);
    }

    public Path directory() {
        return base.getParent();
    }

    /** {@code libertyshop}: the database's name, and the prefix of its backups. */
    public String name() {
        return base.getFileName().toString();
    }
}
