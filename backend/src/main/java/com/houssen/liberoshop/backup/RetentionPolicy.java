package com.houssen.liberoshop.backup;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Which backups to delete, as a pure function of the list and the settings ("grandfather, father,
 * son" rotation).
 *
 * <p>Automatic backups: the {@code keepRecent} newest, plus the last one of each of the
 * {@code keepDaily} most recent days, plus the last one of each of the {@code keepMonthly} most
 * recent months -- an hour ago, yesterday evening and last month's end all stay within reach.
 * Manual and before-restore backups: the {@code keepManual} newest. Startup copies: the
 * {@link BackupConfig#KEEP_STARTUP} newest. The single newest backup is never deleted.
 */
public final class RetentionPolicy {

    private RetentionPolicy() {
    }

    public static List<BackupEntry> toDelete(List<BackupEntry> entries, BackupConfig config) {
        List<BackupEntry> newestFirst = entries.stream()
                .sorted(Comparator.comparing(BackupEntry::createdAt).reversed())
                .toList();
        Set<BackupEntry> keep = new HashSet<>();
        if (!newestFirst.isEmpty()) {
            keep.add(newestFirst.getFirst());
        }

        List<BackupEntry> automatic = ofKind(newestFirst, BackupKind.AUTOMATIC);
        automatic.stream().limit(config.keepRecent()).forEach(keep::add);
        keep.addAll(lastOfEach(automatic, entry -> entry.createdAt().toLocalDate(), config.keepDaily()));
        keep.addAll(lastOfEach(automatic, entry -> YearMonth.from(entry.createdAt()), config.keepMonthly()));

        newestFirst.stream()
                .filter(entry -> entry.kind() == BackupKind.MANUAL || entry.kind() == BackupKind.BEFORE_RESTORE)
                .limit(config.keepManual())
                .forEach(keep::add);
        ofKind(newestFirst, BackupKind.STARTUP).stream().limit(BackupConfig.KEEP_STARTUP).forEach(keep::add);

        return newestFirst.stream().filter(entry -> !keep.contains(entry)).toList();
    }

    private static List<BackupEntry> ofKind(List<BackupEntry> newestFirst, BackupKind kind) {
        return newestFirst.stream().filter(entry -> entry.kind() == kind).toList();
    }

    /** The newest entry of each period, for the {@code periods} most recent periods. */
    private static <P> List<BackupEntry> lastOfEach(List<BackupEntry> newestFirst, Function<BackupEntry, P> period,
                                                    int periods) {
        Map<P, BackupEntry> newestPerPeriod = new LinkedHashMap<>();
        for (BackupEntry entry : newestFirst) {
            newestPerPeriod.putIfAbsent(period.apply(entry), entry);
        }
        return newestPerPeriod.values().stream().limit(periods).toList();
    }
}
