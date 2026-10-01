package com.houssen.liberoshop.backup;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The backups in one folder: each a zip, with a {@code .json} sidecar that says when and why it
 * was made and whether it was verified.
 *
 * <p>The folder is the catalog -- no table lists the backups, since the database is precisely what
 * a backup may have to replace. A zip whose sidecar went missing (copied by hand from a USB key)
 * is still listed, from its name, as not verified.
 *
 * <p>Names are {@code <database>-<yyyyMMdd-HHmmss>-<kind>.zip}. A name coming from a request is
 * only ever looked up among these, never joined to a path as given.
 */
public final class BackupCatalog {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern NAME = Pattern.compile("([A-Za-z0-9_.-]+)-(\\d{8}-\\d{6})-([a-z_]+)\\.zip");

    private final Path directory;

    public BackupCatalog(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    static String fileNameOf(String database, LocalDateTime at, BackupKind kind) {
        return database + "-" + STAMP.format(at) + "-" + kind.name().toLowerCase(Locale.ROOT) + ".zip";
    }

    /** Newest first. An absent folder is an empty catalog, not an error. */
    public List<BackupEntry> list() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<BackupEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> zips = Files.newDirectoryStream(directory, "*.zip")) {
            for (Path zip : zips) {
                read(zip).ifPresent(entries::add);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du dossier " + directory + " impossible.", e);
        }
        entries.sort(Comparator.comparing(BackupEntry::createdAt).reversed());
        return entries;
    }

    /** The backup of that exact name in this folder, if there is one. */
    public Optional<BackupEntry> find(String fileName) {
        if (fileName == null || !NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        return list().stream().filter(entry -> entry.fileName().equals(fileName)).findFirst();
    }

    public Path pathOf(BackupEntry entry) {
        return directory.resolve(entry.fileName());
    }

    public void writeSidecar(BackupEntry entry) {
        try {
            Files.writeString(sidecarOf(entry.fileName()), MAPPER.writeValueAsString(Sidecar.of(entry)));
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Ecriture de la fiche de " + entry.fileName() + " impossible.", e);
        }
    }

    public void delete(BackupEntry entry) {
        try {
            Files.deleteIfExists(pathOf(entry));
            Files.deleteIfExists(sidecarOf(entry.fileName()));
        } catch (IOException e) {
            throw new UncheckedIOException("Suppression de " + entry.fileName() + " impossible.", e);
        }
    }

    private Optional<BackupEntry> read(Path zip) {
        String fileName = zip.getFileName().toString();
        Matcher name = NAME.matcher(fileName);
        if (!name.matches()) {
            return Optional.empty();
        }
        Path sidecar = sidecarOf(fileName);
        try {
            long size = Files.size(zip);
            if (Files.isRegularFile(sidecar)) {
                try {
                    return Optional.of(MAPPER.readValue(Files.readString(sidecar), Sidecar.class).toEntry(fileName, size));
                } catch (JacksonException | IllegalArgumentException e) {
                    // A damaged sidecar: fall back on the name, like a missing one.
                }
            }
            LocalDateTime at = LocalDateTime.parse(name.group(2), STAMP);
            BackupKind kind = BackupKind.valueOf(name.group(3).toUpperCase(Locale.ROOT));
            return Optional.of(new BackupEntry(fileName, at, kind, size, false, 0, 0, "Fiche absente : non vérifiée."));
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Path sidecarOf(String zipName) {
        return directory.resolve(zipName.substring(0, zipName.length() - ".zip".length()) + ".json");
    }

    /** What is written beside each zip. Dates as text: readable by whoever opens the folder. */
    record Sidecar(String createdAt, String kind, boolean verified, long invoiceCount, long productCount,
                   String note) {

        static Sidecar of(BackupEntry entry) {
            return new Sidecar(entry.createdAt().toString(), entry.kind().name(), entry.verified(),
                    entry.invoiceCount(), entry.productCount(), entry.note());
        }

        BackupEntry toEntry(String fileName, long size) {
            return new BackupEntry(fileName, LocalDateTime.parse(createdAt), BackupKind.valueOf(kind), size,
                    verified, invoiceCount, productCount, note);
        }
    }
}
