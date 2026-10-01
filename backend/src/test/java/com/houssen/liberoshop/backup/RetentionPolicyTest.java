package com.houssen.liberoshop.backup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hourly for a while, then one a day, then one a month: and never the newest. */
class RetentionPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 12, 0);

    private static BackupConfig keeping(int recent, int daily, int monthly, int manual) {
        return new BackupConfig(true, 60, recent, daily, monthly, manual, "x", null);
    }

    private static BackupEntry at(LocalDateTime when, BackupKind kind) {
        return new BackupEntry("b-" + when + "-" + kind, when, kind, 1, true, 0, 0, null);
    }

    private static List<BackupEntry> hourlyFor(int hours) {
        List<BackupEntry> entries = new ArrayList<>();
        for (int h = 0; h < hours; h++) {
            entries.add(at(NOW.minusHours(h), BackupKind.AUTOMATIC));
        }
        return entries;
    }

    @Test
    @DisplayName("keeps the recent ones, then the last of each day, then the last of each month")
    void grandfatherFatherSon() {
        List<BackupEntry> entries = hourlyFor(24 * 70); // seventy days of hourly backups
        List<BackupEntry> deleted = RetentionPolicy.toDelete(entries, keeping(24, 30, 3, 10));

        List<BackupEntry> kept = entries.stream().filter(e -> !deleted.contains(e)).toList();
        assertTrue(kept.containsAll(entries.subList(0, 24)), "the last 24 hours, all of them");
        // Last of each of 30 days (today included, already among the recent ones), last of 3 months.
        assertTrue(kept.contains(at(LocalDateTime.of(2026, 9, 20, 23, 0), BackupKind.AUTOMATIC)));
        assertFalse(kept.contains(at(LocalDateTime.of(2026, 9, 20, 22, 0), BackupKind.AUTOMATIC)));
        // Three months, the current one included: October, September, August.
        assertTrue(kept.contains(at(LocalDateTime.of(2026, 8, 31, 23, 0), BackupKind.AUTOMATIC)), "August's last");
        assertFalse(kept.contains(at(LocalDateTime.of(2026, 7, 31, 23, 0), BackupKind.AUTOMATIC)), "July is a 4th month");
        assertTrue(kept.size() < 24 + 30 + 3, "nothing else");
    }

    @Test
    @DisplayName("manual and before-restore backups count against their own limit, startup copies against five")
    void otherKinds() {
        List<BackupEntry> entries = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            entries.add(at(NOW.minusDays(i), BackupKind.MANUAL));
            entries.add(at(NOW.minusDays(i).minusHours(1), BackupKind.BEFORE_RESTORE));
        }
        for (int i = 0; i < 8; i++) {
            entries.add(at(NOW.minusDays(10 + i), BackupKind.STARTUP));
        }
        List<BackupEntry> deleted = RetentionPolicy.toDelete(entries, keeping(24, 0, 0, 3));

        assertEquals(5 + 3, deleted.size(), "5 of the 8 manual ones, 3 of the 8 startup copies");
    }

    @Test
    @DisplayName("the newest backup is kept even when every limit says otherwise")
    void newestAlwaysKept() {
        BackupEntry only = at(NOW, BackupKind.STARTUP);
        assertTrue(RetentionPolicy.toDelete(List.of(only), keeping(1, 0, 0, 1)).isEmpty());
    }

    @Test
    @DisplayName("reads the database file from the datasource URL; memory and server databases have none")
    void databaseFileFromUrl() {
        Optional<DatabaseFile> file = DatabaseFile.fromUrl("jdbc:h2:file:./data/libertyshop;MODE=LEGACY");
        assertEquals("libertyshop", file.orElseThrow().name());
        assertEquals("libertyshop.mv.db", file.get().file().getFileName().toString());
        assertEquals(Path.of("data").toAbsolutePath().normalize(), file.get().directory());
        assertTrue(DatabaseFile.fromUrl("jdbc:h2:./shop").isPresent());
        assertTrue(DatabaseFile.fromUrl("jdbc:h2:mem:test").isEmpty());
        assertTrue(DatabaseFile.fromUrl("jdbc:h2:tcp://localhost/shop").isEmpty());
        assertTrue(DatabaseFile.fromUrl(null).isEmpty());
    }
}
