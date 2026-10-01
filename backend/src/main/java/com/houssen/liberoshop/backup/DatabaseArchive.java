package com.houssen.liberoshop.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Zipping a closed database file, and getting it back out of a backup.
 *
 * <p>Both write to a temporary file first and move it into place: a power cut halfway leaves the
 * previous file untouched, never half of a new one.
 */
final class DatabaseArchive {

    private DatabaseArchive() {
    }

    /** Copies a database file nobody has open into a zip, under its own name. */
    static void zip(Path databaseFile, Path zip) throws IOException {
        Files.createDirectories(zip.getParent());
        Path temporary = zip.resolveSibling(zip.getFileName() + ".part");
        try (OutputStream out = Files.newOutputStream(temporary);
             ZipOutputStream zipOut = new ZipOutputStream(out)) {
            zipOut.putNextEntry(new ZipEntry(databaseFile.getFileName().toString()));
            Files.copy(databaseFile, zipOut);
            zipOut.closeEntry();
        }
        Files.move(temporary, zip, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Writes the database file a backup holds to {@code target}.
     *
     * @throws IOException when the zip holds no {@code .mv.db} -- not a backup of this application
     */
    static void extractDatabase(Path zip, Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = Files.newInputStream(zip); ZipInputStream zipIn = new ZipInputStream(in)) {
            for (ZipEntry entry = zipIn.getNextEntry(); entry != null; entry = zipIn.getNextEntry()) {
                if (!entry.isDirectory() && entry.getName().endsWith(DatabaseFile.SUFFIX)) {
                    Files.copy(zipIn, temporary, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException(zip.getFileName() + " ne contient aucune base de donnees.");
    }
}
