package com.houssen.liberoshop.backup;

import com.houssen.liberoshop.service.BusinessCalendar;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.BackupOverviewResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * The database's backups: taken on schedule and on demand, verified, copied to a second place,
 * rotated, and restored.
 *
 * <p>Each step is someone else's: {@link BackupEngine} copies and verifies, {@link BackupCatalog}
 * knows a folder's backups, {@link RetentionPolicy} decides what goes, {@link BackupConfigStore}
 * keeps the settings, {@link StartupDatabaseGuard} carries out a restore. This class decides when.
 *
 * <p>One backup at a time: the scheduler and the "Sauvegarder maintenant" button share a lock.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    /** Staging copy a restore reads from, so that rotation cannot delete it in the meantime. */
    static final String RESTORE_STAGING = "restore-staging.zip";

    private final Optional<DatabaseFile> database;
    private final BackupEngine engine;
    private final BusinessCalendar calendar;
    private final ApplicationRestarter restarter;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile BackupConfig config;
    private volatile String lastError;
    private volatile LocalDateTime lastErrorAt;
    private volatile String secondaryError;

    @Autowired
    public BackupService(Environment environment, DataSource dataSource, BusinessCalendar calendar,
                         ApplicationRestarter restarter) {
        this(DatabaseFile.fromUrl(environment.getProperty("spring.datasource.url")),
                DatabaseFile.fromUrl(environment.getProperty("spring.datasource.url"))
                        .map(file -> (BackupEngine) new H2BackupEngine(dataSource, file,
                                environment.getProperty("spring.datasource.username", "sa"),
                                environment.getProperty("spring.datasource.password", "")))
                        .orElse(null),
                calendar, restarter);
    }

    BackupService(Optional<DatabaseFile> database, BackupEngine engine, BusinessCalendar calendar,
                  ApplicationRestarter restarter) {
        this.database = database;
        this.engine = engine;
        this.calendar = calendar;
        this.restarter = restarter;
        this.config = database.map(BackupConfigStore::load).orElse(null);
    }

    public boolean available() {
        return database.isPresent() && engine != null;
    }

    // ------------------------------------------------------------------ taking backups

    public BackupEntry backupNow(BackupKind kind, String note) {
        DatabaseFile db = requireAvailable();
        lock.lock();
        try {
            LocalDateTime now = calendar.now();
            BackupCatalog primary = primary();
            String name = BackupCatalog.fileNameOf(db.name(), now, kind);
            Path zip = primary.directory().resolve(name);
            engine.snapshot(zip);
            BackupEngine.Verification check = engine.verify(zip);
            BackupEntry entry = new BackupEntry(name, now, kind, Files.size(zip), check.readable(),
                    check.invoices(), check.products(), check.readable() ? note : "Illisible : " + check.error());
            primary.writeSidecar(entry);
            copyToSecondary(primary, entry);
            prune(primary);
            secondary().ifPresent(this::pruneQuietly);
            lastError = check.readable() ? null : entry.note();
            lastErrorAt = check.readable() ? null : now;
            return entry;
        } catch (IOException | UncheckedIOException e) {
            lastError = e.getMessage();
            lastErrorAt = calendar.now();
            log.error("Sauvegarde ({}) impossible.", kind, e);
            throw new BusinessRuleException("BACKUP_FAILED", "Sauvegarde impossible : " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Every minute: an automatic backup when one is due, and the verification of the copies the
     * startup guard made without a database to check them against.
     */
    @Scheduled(initialDelay = 120_000, fixedDelay = 60_000)
    public void tick() {
        if (!available()) {
            return;
        }
        verifyStartupCopies();
        if (config.enabled() && isDue(calendar.now())) {
            try {
                backupNow(BackupKind.AUTOMATIC, null);
            } catch (BusinessRuleException e) {
                // Recorded as the last error, shown on the screen; retried at the next interval.
            }
        }
    }

    boolean isDue(LocalDateTime now) {
        return lastAutomatic().map(last -> !now.isBefore(last.plusMinutes(config.intervalMinutes()))).orElse(true);
    }

    // ------------------------------------------------------------------ reading

    public BackupOverviewResponse overview() {
        if (!available()) {
            return new BackupOverviewResponse(false, null, null, List.of(), BackupConfig.INTERVALS);
        }
        BackupConfig current = config;
        List<BackupEntry> entries = primary().list();
        Set<String> inSecondary = secondary()
                .map(catalog -> catalog.list().stream().map(BackupEntry::fileName).collect(Collectors.toSet()))
                .orElse(Set.of());
        LocalDateTime lastSuccess = entries.stream().filter(BackupEntry::verified)
                .map(BackupEntry::createdAt).max(Comparator.naturalOrder()).orElse(null);
        LocalDateTime next = current.enabled()
                ? lastAutomatic().map(last -> last.plusMinutes(current.intervalMinutes())).orElse(calendar.now())
                : null;
        Duration tolerance = Duration.ofMinutes(Math.max(24 * 60, 2L * current.intervalMinutes()));
        boolean stale = lastSuccess == null || lastSuccess.plus(tolerance).isBefore(calendar.now());

        BackupOverviewResponse.Status status = new BackupOverviewResponse.Status(lastSuccess, next, lastError,
                lastErrorAt, stale, database.get().file().toString(), sizeOf(database.get().file()),
                freeSpace(primary().directory()), current.hasSecondary(), secondaryReachable(), secondaryError,
                restarter.canRestart());
        List<BackupOverviewResponse.Item> items = entries.stream()
                .map(entry -> BackupOverviewResponse.Item.of(entry, inSecondary.contains(entry.fileName())))
                .toList();
        return new BackupOverviewResponse(true, status, current, items, BackupConfig.INTERVALS);
    }

    /** The zip to download: from the main folder, or the second one if only there. */
    public Path fileOf(String fileName) {
        requireAvailable();
        BackupCatalog primary = primary();
        Optional<BackupEntry> found = primary.find(fileName);
        if (found.isPresent()) {
            return primary.pathOf(found.get());
        }
        return secondary().flatMap(catalog -> catalog.find(fileName).map(catalog::pathOf))
                .orElseThrow(() -> new ResourceNotFoundException("Sauvegarde introuvable : " + fileName));
    }

    // ------------------------------------------------------------------ settings

    public BackupConfig updateConfig(BackupConfig requested) {
        DatabaseFile db = requireAvailable();
        validate(requested);
        lock.lock();
        try {
            BackupConfigStore.save(db, requested);
            config = requested;
            secondaryError = null;
            prune(primary());
            secondary().ifPresent(this::pruneQuietly);
            return requested;
        } finally {
            lock.unlock();
        }
    }

    private void validate(BackupConfig c) {
        if (!BackupConfig.INTERVALS.contains(c.intervalMinutes())) {
            throw new BusinessRuleException("INVALID_INTERVAL", "Intervalle de sauvegarde non proposé.");
        }
        requireRange("sauvegardes récentes", c.keepRecent(), 1, 500);
        requireRange("jours", c.keepDaily(), 0, 366);
        requireRange("mois", c.keepMonthly(), 0, 120);
        requireRange("sauvegardes manuelles", c.keepManual(), 1, 100);
        if (c.directory() == null || c.directory().isBlank()) {
            throw new BusinessRuleException("INVALID_DIRECTORY", "Indiquez le dossier des sauvegardes.");
        }
        Path main = Path.of(c.directory()).toAbsolutePath().normalize();
        if (!writable(main)) {
            throw new BusinessRuleException("INVALID_DIRECTORY", "Le dossier " + main + " ne peut pas être écrit.");
        }
        if (c.hasSecondary()) {
            Path second = Path.of(c.secondaryDirectory());
            if (!second.isAbsolute()) {
                throw new BusinessRuleException("INVALID_DIRECTORY",
                        "Le second emplacement doit être un chemin complet, par exemple E:\\Sauvegardes.");
            }
            if (second.toAbsolutePath().normalize().equals(main)) {
                throw new BusinessRuleException("INVALID_DIRECTORY",
                        "Le second emplacement doit être différent du premier : sinon il ne protège de rien.");
            }
        }
    }

    private static void requireRange(String what, int value, int min, int max) {
        if (value < min || value > max) {
            throw new BusinessRuleException("INVALID_RETENTION",
                    "Nombre de " + what + " à conserver : entre " + min + " et " + max + ".");
        }
    }

    // ------------------------------------------------------------------ restoring

    /**
     * Puts a backup back: the current database is backed up first, then the server restarts and
     * the startup guard replaces the file before anything opens it.
     */
    public void restore(String fileName) {
        DatabaseFile db = requireAvailable();
        if (!restarter.canRestart()) {
            throw new BusinessRuleException("RESTART_UNAVAILABLE",
                    "Ce serveur ne peut pas redémarrer seul : restaurez en l'arrêtant.");
        }
        Path source = fileOf(fileName);
        BackupEngine.Verification check = engine.verify(source);
        if (!check.readable()) {
            throw new BusinessRuleException("BACKUP_UNREADABLE",
                    "Cette sauvegarde est illisible, elle ne peut pas être restaurée : " + check.error());
        }
        backupNow(BackupKind.BEFORE_RESTORE, "Base avant la restauration de " + fileName + ".");
        try {
            Path staging = db.directory().resolve(RESTORE_STAGING);
            Files.copy(source, staging, StandardCopyOption.REPLACE_EXISTING);
            PendingRestore.request(db, staging);
        } catch (IOException e) {
            throw new BusinessRuleException("RESTORE_FAILED", "Préparation de la restauration impossible : " + e.getMessage());
        }
        log.warn("Restauration de {} programmee : redemarrage.", fileName);
        restarter.restartSoon();
    }

    // ------------------------------------------------------------------ internals

    private DatabaseFile requireAvailable() {
        return database.filter(db -> engine != null).orElseThrow(() -> new BusinessRuleException("BACKUP_UNAVAILABLE",
                "Sauvegarde indisponible : la base n'est pas un fichier de ce serveur."));
    }

    private BackupCatalog primary() {
        return new BackupCatalog(Path.of(config.directory()).toAbsolutePath().normalize());
    }

    private Optional<BackupCatalog> secondary() {
        BackupConfig current = config;
        return current.hasSecondary() ? Optional.of(new BackupCatalog(Path.of(current.secondaryDirectory())))
                : Optional.empty();
    }

    private Optional<LocalDateTime> lastAutomatic() {
        return primary().list().stream().filter(entry -> entry.kind() == BackupKind.AUTOMATIC)
                .map(BackupEntry::createdAt).max(Comparator.naturalOrder());
    }

    /** A failure here does not fail the backup: the first copy exists; the screen says the second is missing. */
    private void copyToSecondary(BackupCatalog primary, BackupEntry entry) {
        Optional<BackupCatalog> second = secondary();
        if (second.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(second.get().directory());
            Files.copy(primary.pathOf(entry), second.get().pathOf(entry), StandardCopyOption.REPLACE_EXISTING);
            second.get().writeSidecar(entry);
            secondaryError = null;
        } catch (IOException | RuntimeException e) {
            secondaryError = "Copie vers " + second.get().directory() + " impossible : " + e.getMessage();
            log.warn(secondaryError);
        }
    }

    private void prune(BackupCatalog catalog) {
        RetentionPolicy.toDelete(catalog.list(), config).forEach(catalog::delete);
    }

    private void pruneQuietly(BackupCatalog catalog) {
        try {
            prune(catalog);
        } catch (RuntimeException e) {
            log.warn("Rotation du second emplacement impossible : {}", e.getMessage());
        }
    }

    private void verifyStartupCopies() {
        BackupCatalog primary = primary();
        for (BackupEntry entry : primary.list()) {
            if (entry.kind() == BackupKind.STARTUP && !entry.verified() && entry.note() != null
                    && entry.note().startsWith("Copie au démarrage")) {
                BackupEngine.Verification check = engine.verify(primary.pathOf(entry));
                primary.writeSidecar(entry.withVerification(check.readable(), check.invoices(), check.products(),
                        check.readable() ? "Copie au démarrage, vérifiée." : "Illisible : " + check.error()));
            }
        }
    }

    private boolean secondaryReachable() {
        return secondary().map(catalog -> writable(catalog.directory())).orElse(false);
    }

    private static boolean writable(Path folder) {
        try {
            Files.createDirectories(folder);
            Path probe = Files.createTempFile(folder, ".probe", ".tmp");
            Files.delete(probe);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    private static long freeSpace(Path folder) {
        try {
            Files.createDirectories(folder);
            return Files.getFileStore(folder).getUsableSpace();
        } catch (IOException e) {
            return 0;
        }
    }
}
