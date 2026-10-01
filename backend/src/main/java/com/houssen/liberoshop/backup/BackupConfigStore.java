package com.houssen.liberoshop.backup;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes {@link BackupConfig} in {@code backup-settings.json}, beside the database.
 *
 * <p>Static as well as a bean's helper: the startup guard needs the backup folder before any bean
 * exists. A file that cannot be read falls back to the defaults -- backups keep running with them
 * rather than stopping because a setting was mistyped.
 */
public final class BackupConfigStore {

    static final String FILE_NAME = "backup-settings.json";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private BackupConfigStore() {
    }

    public static BackupConfig load(DatabaseFile database) {
        Path file = fileOf(database);
        if (!Files.isRegularFile(file)) {
            return BackupConfig.defaults(database);
        }
        try {
            return MAPPER.readValue(Files.readString(file), BackupConfig.class);
        } catch (IOException | JacksonException e) {
            return BackupConfig.defaults(database);
        }
    }

    /** Written to a temporary file then moved: a crash mid-write never leaves half a file. */
    public static void save(DatabaseFile database, BackupConfig config) {
        Path file = fileOf(database);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temporary, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(config));
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Ecriture de " + file + " impossible.", e);
        }
    }

    private static Path fileOf(DatabaseFile database) {
        return database.directory().resolve(FILE_NAME);
    }
}
