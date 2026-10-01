package com.houssen.liberoshop.backup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * "Restore this backup at the next start": a one-line file beside the database naming the zip.
 *
 * <p>The database cannot be replaced while it is open, so a restore is asked for while running and
 * carried out by {@link StartupDatabaseGuard} before the next start opens it. A marker that could
 * not be carried out is renamed, so a broken backup does not make every start try again.
 */
final class PendingRestore {

    static final String FILE_NAME = "restore-pending.txt";

    private PendingRestore() {
    }

    static void request(DatabaseFile database, Path backup) {
        try {
            Files.writeString(markerOf(database), backup.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Programmation de la restauration impossible.", e);
        }
    }

    static Optional<Path> requested(DatabaseFile database) {
        Path marker = markerOf(database);
        if (!Files.isRegularFile(marker)) {
            return Optional.empty();
        }
        try {
            String path = Files.readString(marker).trim();
            return path.isEmpty() ? Optional.empty() : Optional.of(Path.of(path));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    static void done(DatabaseFile database) throws IOException {
        Files.deleteIfExists(markerOf(database));
    }

    static void failed(DatabaseFile database) throws IOException {
        Path marker = markerOf(database);
        if (Files.exists(marker)) {
            Files.move(marker, marker.resolveSibling(FILE_NAME + ".failed"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path markerOf(DatabaseFile database) {
        return database.directory().resolve(FILE_NAME);
    }
}
