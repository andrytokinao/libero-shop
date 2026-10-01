package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.backup.BackupConfig;
import com.houssen.liberoshop.backup.BackupEntry;
import com.houssen.liberoshop.backup.BackupKind;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The backups screen in one call: how things stand, the settings, and every backup.
 *
 * @param available false when the database is not a file this server can copy (in memory, or
 *                  served by another process): the screen then explains instead of offering buttons
 */
public record BackupOverviewResponse(boolean available,
                                     Status status,
                                     BackupConfig config,
                                     List<Item> backups,
                                     List<Integer> intervals) {

    /**
     * @param stale             no backup has succeeded for too long: shown in red
     * @param secondaryReachable the second folder exists and can be written -- false when the USB key
     *                          was taken away
     * @param canRestore        the server can restart itself to carry out a restore
     */
    public record Status(LocalDateTime lastSuccessAt,
                         LocalDateTime nextAutomaticAt,
                         String lastError,
                         LocalDateTime lastErrorAt,
                         boolean stale,
                         String databaseFile,
                         long databaseSizeBytes,
                         long freeSpaceBytes,
                         boolean secondaryConfigured,
                         boolean secondaryReachable,
                         String secondaryError,
                         boolean canRestore) {
    }

    /** @param inSecondary also copied to the second folder */
    public record Item(String fileName, LocalDateTime createdAt, BackupKind kind, long sizeBytes,
                       boolean verified, long invoiceCount, long productCount, String note,
                       boolean inSecondary) {

        public static Item of(BackupEntry entry, boolean inSecondary) {
            return new Item(entry.fileName(), entry.createdAt(), entry.kind(), entry.sizeBytes(), entry.verified(),
                    entry.invoiceCount(), entry.productCount(), entry.note(), inSecondary);
        }
    }
}
