package com.houssen.liberoshop.backup;

import java.util.List;

/**
 * How the shop's database is backed up: how often, how many are kept, where.
 *
 * <p>Kept in a file beside the database, not in it: restoring last week's database must not also
 * bring back last week's backup settings -- nor lose track of the folder the backups are in.
 *
 * @param enabled            automatic backups on; manual ones always work
 * @param intervalMinutes    between two automatic backups, one of {@link #INTERVALS}
 * @param keepRecent         the most recent automatic backups kept, whatever their age
 * @param keepDaily          days for which the day's last automatic backup is kept
 * @param keepMonthly        months for which the month's last automatic backup is kept
 * @param keepManual         manual and before-restore backups kept
 * @param directory          where backups are written
 * @param secondaryDirectory a second place every backup is copied to -- a USB key, a second disk,
 *                           a shared folder on another machine; null for none
 */
public record BackupConfig(boolean enabled,
                           int intervalMinutes,
                           int keepRecent,
                           int keepDaily,
                           int keepMonthly,
                           int keepManual,
                           String directory,
                           String secondaryDirectory) {

    /** From every quarter of an hour to once a day: what a shop can choose from. */
    public static final List<Integer> INTERVALS = List.of(15, 30, 60, 120, 240, 720, 1440);

    /** Startup copies kept: enough to step back over a few failed upgrades, no more. */
    public static final int KEEP_STARTUP = 5;

    /** Hourly for a day, daily for a month, monthly for a year: a mistake found weeks later can be undone. */
    public static BackupConfig defaults(DatabaseFile database) {
        return new BackupConfig(true, 60, 24, 30, 12, 10,
                database.directory().resolve("backups").toString(), null);
    }

    public boolean hasSecondary() {
        return secondaryDirectory != null && !secondaryDirectory.isBlank();
    }
}
