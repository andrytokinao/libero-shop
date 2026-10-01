package com.houssen.liberoshop.backup;

import com.houssen.liberoshop.service.BusinessCalendar;
import com.houssen.liberoshop.service.exception.BusinessRuleException;
import com.houssen.liberoshop.service.exception.ResourceNotFoundException;
import com.houssen.liberoshop.web.dto.BackupOverviewResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Backups of a real H2 file: taken live, verified, copied to a second folder, rotated, protected. */
class BackupServiceTest {

    @TempDir
    Path folder;

    private DatabaseFile database;
    private JdbcTemplate jdbc;
    private MovableClock clock;
    private BackupService service;

    /** A clock the test moves forward, so backups get distinct names and intervals elapse. */
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-01T09:00:00Z");

        void advanceMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void shop() {
        database = DatabaseFile.fromUrl("jdbc:h2:file:" + folder.resolve("shop")).orElseThrow();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:file:" + database.base() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table invoice (id int)");
        jdbc.execute("create table product (id int)");
        jdbc.update("insert into invoice values (1), (2)");
        jdbc.update("insert into product values (1)");
        clock = new MovableClock();
        service = new BackupService(Optional.of(database), new H2BackupEngine(dataSource, database, "sa", ""),
                new BusinessCalendar(clock), new ApplicationRestarter(null));
    }

    @AfterEach
    void close() {
        jdbc.execute("SHUTDOWN");
    }

    @Test
    @DisplayName("a backup is taken while the database is open, then re-opened and counted")
    void backupIsVerified() {
        BackupEntry entry = service.backupNow(BackupKind.MANUAL, "test");

        assertTrue(entry.verified());
        assertEquals(2, entry.invoiceCount());
        assertEquals(1, entry.productCount());
        assertTrue(Files.isRegularFile(folder.resolve("backups").resolve(entry.fileName())));

        BackupOverviewResponse overview = service.overview();
        assertTrue(overview.available());
        assertEquals(1, overview.backups().size());
        assertFalse(overview.status().stale());
    }

    @Test
    @DisplayName("every backup is also copied to the second folder, and listed as such")
    void secondaryCopy() {
        Path usb = folder.resolve("usb");
        BackupConfig config = BackupConfig.defaults(database);
        service.updateConfig(new BackupConfig(true, 60, 24, 30, 12, 10, config.directory(), usb.toString()));

        BackupEntry entry = service.backupNow(BackupKind.MANUAL, null);

        assertTrue(Files.isRegularFile(usb.resolve(entry.fileName())));
        assertTrue(service.overview().backups().getFirst().inSecondary());
        assertTrue(service.overview().status().secondaryReachable());
    }

    @Test
    @DisplayName("an automatic backup is taken when due, not again before the interval has passed")
    void schedule() {
        service.tick();
        service.tick();
        assertEquals(1, service.overview().backups().size(), "due once at start, not twice");

        clock.advanceMinutes(60);
        service.tick();
        assertEquals(2, service.overview().backups().size());
    }

    @Test
    @DisplayName("rotation follows the settings as soon as they change")
    void rotation() {
        for (int i = 0; i < 4; i++) {
            service.backupNow(BackupKind.MANUAL, null);
            clock.advanceMinutes(1);
        }
        BackupConfig config = BackupConfig.defaults(database);
        service.updateConfig(new BackupConfig(true, 60, 24, 30, 12, 2, config.directory(), null));
        assertEquals(2, service.overview().backups().size());
    }

    @Test
    @DisplayName("settings that would not protect anything are refused")
    void invalidSettings() {
        String main = BackupConfig.defaults(database).directory();
        assertThrows(BusinessRuleException.class,
                () -> service.updateConfig(new BackupConfig(true, 7, 24, 30, 12, 10, main, null)));
        assertThrows(BusinessRuleException.class,
                () -> service.updateConfig(new BackupConfig(true, 60, 24, 30, 12, 10, main, main)));
        assertThrows(BusinessRuleException.class,
                () -> service.updateConfig(new BackupConfig(true, 60, 24, 30, 12, 10, main, "relative/path")));
    }

    @Test
    @DisplayName("a file name from a request is only ever looked up among the backups")
    void noPathTraversal() {
        service.backupNow(BackupKind.MANUAL, null);
        assertThrows(ResourceNotFoundException.class, () -> service.fileOf("../shop.mv.db"));
        assertThrows(ResourceNotFoundException.class, () -> service.fileOf("..\\..\\secret.zip"));
    }

    @Test
    @DisplayName("without a way to restart, a restore is refused before anything is touched")
    void restoreNeedsRestart() {
        BackupEntry entry = service.backupNow(BackupKind.MANUAL, null);
        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
                () -> service.restore(entry.fileName()));
        assertEquals("RESTART_UNAVAILABLE", refused.code());
        assertEquals(1, service.overview().backups().size(), "no before-restore backup taken");
    }
}
