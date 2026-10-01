package com.houssen.liberoshop.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Looks after the database file in the only moment nobody has it open: at startup, before the
 * datasource exists.
 *
 * <ul>
 *   <li>A restore was asked for: the backup's database replaces the current file. (The current one
 *       was already backed up when the restore was asked for.)</li>
 *   <li>Otherwise: the file is copied as a {@link BackupKind#STARTUP} backup, before Hibernate and
 *       the startup migrations change its schema -- an upgrade that goes wrong can be undone.</li>
 * </ul>
 *
 * <p>Registered from {@code BackendApplication.main}, like the license listener, which keeps it out
 * of the tests. A failure here is logged and the start goes on with the database as it is: a shop
 * that cannot open is worse off than one with an older backup.
 */
public class StartupDatabaseGuard implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupDatabaseGuard.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        DatabaseFile.fromUrl(event.getEnvironment().getProperty("spring.datasource.url"))
                .ifPresent(database -> protect(database, LocalDateTime.now()));
    }

    /** What happened, for the log and the tests. */
    enum Outcome { NOTHING_TO_DO, RESTORED, RESTORE_FAILED, COPIED, COPY_FAILED }

    static Outcome protect(DatabaseFile database, LocalDateTime now) {
        Optional<Path> restore = PendingRestore.requested(database);
        if (restore.isPresent()) {
            return restore(database, restore.get());
        }
        if (!Files.isRegularFile(database.file())) {
            return Outcome.NOTHING_TO_DO;
        }
        BackupCatalog catalog = new BackupCatalog(Path.of(BackupConfigStore.load(database).directory()));
        String name = BackupCatalog.fileNameOf(database.name(), now, BackupKind.STARTUP);
        try {
            Path zip = catalog.directory().resolve(name);
            DatabaseArchive.zip(database.file(), zip);
            catalog.writeSidecar(new BackupEntry(name, now, BackupKind.STARTUP, Files.size(zip), false, 0, 0,
                    "Copie au démarrage, avant la mise à jour du schéma."));
            log.info("Copie de la base au demarrage : {}", zip);
            return Outcome.COPIED;
        } catch (IOException | RuntimeException e) {
            log.error("Copie de la base au demarrage impossible ; demarrage poursuivi.", e);
            return Outcome.COPY_FAILED;
        }
    }

    private static Outcome restore(DatabaseFile database, Path backup) {
        try {
            DatabaseArchive.extractDatabase(backup, database.file());
            PendingRestore.done(database);
            // The staging copy the service made so that rotation could not delete the backup meanwhile.
            if (backup.getFileName().toString().equals(BackupService.RESTORE_STAGING)) {
                Files.deleteIfExists(backup);
            }
            log.warn("Base restauree depuis {}.", backup);
            return Outcome.RESTORED;
        } catch (IOException e) {
            log.error("Restauration depuis {} impossible : la base actuelle est conservee.", backup, e);
            try {
                PendingRestore.failed(database);
            } catch (IOException ignored) {
                // The marker stays; the next start will log the same failure.
            }
            return Outcome.RESTORE_FAILED;
        }
    }
}
