package com.houssen.liberoshop.backup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** At startup, with the database closed: a pending restore is carried out, otherwise the file is copied. */
class StartupDatabaseGuardTest {

    @TempDir
    Path folder;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 8, 0);

    private DatabaseFile database() {
        return DatabaseFile.fromUrl("jdbc:h2:file:" + folder.resolve("shop")).orElseThrow();
    }

    /** Runs statements on the database, then closes it as the stopped server would have. */
    private List<Integer> session(DatabaseFile database, String... statements) {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:file:" + database.base(), "sa", ""));
        for (String statement : statements) {
            jdbc.execute(statement);
        }
        List<Integer> ids = jdbc.queryForList("select id from invoice order by id", Integer.class);
        jdbc.execute("SHUTDOWN");
        return ids;
    }

    @Test
    @DisplayName("an existing database is copied before the start changes its schema")
    void copiesAtStartup() {
        DatabaseFile database = database();
        session(database, "create table invoice (id int)", "insert into invoice values (1)");

        assertEquals(StartupDatabaseGuard.Outcome.COPIED, StartupDatabaseGuard.protect(database, NOW));

        List<BackupEntry> backups = new BackupCatalog(folder.resolve("backups")).list();
        assertEquals(1, backups.size());
        assertEquals(BackupKind.STARTUP, backups.getFirst().kind());
    }

    @Test
    @DisplayName("a fresh installation has nothing to copy")
    void freshInstallation() {
        assertEquals(StartupDatabaseGuard.Outcome.NOTHING_TO_DO, StartupDatabaseGuard.protect(database(), NOW));
    }

    @Test
    @DisplayName("a pending restore puts the backup's database back, and is done once")
    void restoresBackup() throws Exception {
        DatabaseFile database = database();
        session(database, "create table invoice (id int)", "insert into invoice values (1)");
        Path backup = folder.resolve(BackupService.RESTORE_STAGING);
        DatabaseArchive.zip(database.file(), backup);
        assertEquals(List.of(1, 2), session(database, "insert into invoice values (2)"));

        PendingRestore.request(database, backup);
        assertEquals(StartupDatabaseGuard.Outcome.RESTORED, StartupDatabaseGuard.protect(database, NOW));

        assertEquals(List.of(1), session(database), "the database as it was in the backup");
        assertTrue(PendingRestore.requested(database).isEmpty(), "not restored again at the next start");
        assertFalse(Files.exists(backup), "the staging copy is cleaned up");
    }

    @Test
    @DisplayName("a backup that cannot be restored leaves the database untouched, and is not retried forever")
    void brokenBackup() throws Exception {
        DatabaseFile database = database();
        session(database, "create table invoice (id int)", "insert into invoice values (1)");
        Path notABackup = Files.writeString(folder.resolve("broken.zip"), "not a zip");

        PendingRestore.request(database, notABackup);
        assertEquals(StartupDatabaseGuard.Outcome.RESTORE_FAILED, StartupDatabaseGuard.protect(database, NOW));

        assertEquals(List.of(1), session(database));
        assertTrue(PendingRestore.requested(database).isEmpty());
    }
}
