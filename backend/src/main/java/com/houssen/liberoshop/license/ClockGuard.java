package com.houssen.liberoshop.license;

import com.houssen.liberoshop.license.TrialRegistry.DatabaseTrialStore;
import com.houssen.liberoshop.license.TrialRegistry.FileStore;
import com.houssen.liberoshop.license.TrialRegistry.TrialStore;
import com.houssen.liberoshop.license.TrialRegistry.WindowsRegistryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Defends against the simplest way to extend a license for free: turning the system
 * clock back.
 *
 * <p>It records the latest date the application has ever seen. Evaluation then uses
 * {@code max(today, lastSeen)}, so winding the clock back buys nothing -- the license keeps
 * ageing.
 *
 * <p>The record is kept in several places: a file next to the license, a row of the
 * application database and, on Windows, a registry value. The latest valid copy wins and
 * the others are brought up to it, so erasing the file -- or pointing the configured path
 * at an empty folder -- no longer erases history: the database still remembers. Every copy
 * is authenticated with an HMAC keyed on the embedded public key and the machine
 * fingerprint, so it cannot be edited by hand nor lifted from another installation.
 *
 * <p>A clock held still is caught too, as long as the server keeps running: the date never
 * stays behind the time actually elapsed since the guard first looked at it, measured on
 * the monotonic clock the user cannot set.
 *
 * <p>Failures are never fatal. A read-only directory or a missing database degrades to
 * fewer copies rather than blocking the till; only the loss of every copy is reported, as
 * a warning, since that is the one case in which the guard protects nothing. This is a
 * speed bump against casual tampering, not the primary control. The signature is.
 */
class ClockGuard {

    private static final Logger log = LoggerFactory.getLogger(ClockGuard.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Domain separation, so the derived key cannot collide with any other use.
     *
     * <p>Keeps the product's former name on purpose, and must: it is an input to the key that
     * authenticates the state file. Renaming it would make every installed file fail its
     * integrity check, be ignored, and hand back the very clock rollback this class exists to
     * defeat. Invisible to customers -- nothing ever prints it.
     */
    private static final byte[] KEY_CONTEXT = "liberty-shop/clock-guard/v1".getBytes(StandardCharsets.UTF_8);

    /** Row of the shared marker table, and registry value, holding the database and registry copies. */
    static final String DATABASE_KEY = "clock.last-seen";
    static final String REGISTRY_VALUE = "LastSeen";

    private static final long NANOS_PER_DAY = TimeUnit.DAYS.toNanos(1);

    private final boolean enabled;
    private final List<TrialStore> stores;
    private final byte[] key;
    private final LongSupplier monotonicNanos;

    /**
     * In-memory mirror of the stored date. {@code effectiveDate} runs on every guarded
     * write -- once per sale -- so the copies are read once and written only when the day
     * actually advances, rather than on every call.
     */
    private volatile LocalDate lastSeen;
    private volatile boolean loaded;
    /** Set when a copy was missing, altered or behind: the next write brings it back. */
    private volatile boolean repairNeeded;

    /** Date and monotonic instant of the first evaluation: the floor a held clock cannot go below. */
    private LocalDate anchorDate;
    private long anchorNanos;

    /** Last rollback reported, so a held clock is logged once a day, not once per sale. */
    private volatile LocalDate reportedRollback;
    private volatile boolean unrecordedReported;

    /**
     * Production wiring.
     *
     * @param stateFile    the file copy, normally beside the license file
     * @param trustAnchor  encoded public key of the build, mixed into the HMAC key
     * @param fingerprint  machine fingerprint, mixed into the HMAC key
     * @param enabled      lets deployments switch the guard off if it ever gets in the way
     * @param dataSource   application database, resolved lazily; {@code null} before any bean exists
     * @param systemWide   whether to keep a copy outside the application too (the registry), like the trial
     */
    ClockGuard(Path stateFile, byte[] trustAnchor, String fingerprint, boolean enabled,
               Supplier<DataSource> dataSource, boolean systemWide) {
        this(enabled, defaultStores(stateFile, dataSource, systemWide), trustAnchor, fingerprint, System::nanoTime);
    }

    /**
     * @param stores         where the date is replicated; all are read, the latest wins, all are repaired
     * @param monotonicNanos a clock that only moves forward, whatever the system date says
     */
    ClockGuard(boolean enabled, List<TrialStore> stores, byte[] trustAnchor, String fingerprint,
               LongSupplier monotonicNanos) {
        this.enabled = enabled;
        this.stores = List.copyOf(stores);
        this.key = deriveKey(trustAnchor, fingerprint);
        this.monotonicNanos = monotonicNanos;
    }

    /**
     * Returns the date the license should be evaluated against, and records it.
     *
     * @param today the current system date
     * @return {@code today}, or a later date when the clock has moved backwards or stood still
     */
    LocalDate effectiveDate(LocalDate today) {
        if (!enabled) {
            return today;
        }
        loadOnce();
        LocalDate effective = latest(latest(today, lastSeen), elapsedFloor(today));
        if (effective.isAfter(today) && !effective.equals(reportedRollback)) {
            reportedRollback = effective;
            log.warn("System clock appears to have moved backwards or stopped: system date {}, "
                    + "latest date established {}. Evaluating the license against {}.", today, effective, effective);
        }
        if (lastSeen == null || effective.isAfter(lastSeen) || repairNeeded) {
            record(effective);
        }
        return effective;
    }

    private void loadOnce() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (!loaded) {
                lastSeen = readLatest();
                loaded = true;
            }
        }
    }

    /** The latest authentic copy; any copy that is absent, altered or older is marked for repair. */
    private LocalDate readLatest() {
        List<LocalDate> readings = new ArrayList<>(stores.size());
        for (TrialStore store : stores) {
            String raw = store.read();
            LocalDate date = parse(raw);
            if (raw != null && !raw.isBlank() && date == null) {
                // Hand-edited or copied from another machine: ignore it rather than trust it.
                log.warn("License clock state at {} failed its integrity check and was ignored.", store.describe());
            }
            readings.add(date);
        }
        LocalDate latest = readings.stream().filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo).orElse(null);
        repairNeeded = latest != null && readings.stream().anyMatch(date -> !latest.equals(date));
        return latest;
    }

    /**
     * The earliest date it can be, given how long this process has been watching: a clock
     * that has not moved in thirty hours is wrong by at least a day.
     */
    private synchronized LocalDate elapsedFloor(LocalDate today) {
        long now = monotonicNanos.getAsLong();
        if (anchorDate == null) {
            anchorDate = latest(today, lastSeen);
            anchorNanos = now;
            return anchorDate;
        }
        return anchorDate.plusDays(Math.max(0, now - anchorNanos) / NANOS_PER_DAY);
    }

    private synchronized void record(LocalDate date) {
        String record = recordOf(date);
        int kept = 0;
        for (TrialStore store : stores) {
            if (store.write(record)) {
                kept++;
            }
        }
        lastSeen = date;
        repairNeeded = false;
        if (kept == 0 && !unrecordedReported) {
            // The one failure worth a warning: with no copy at all, a restart forgets the date.
            unrecordedReported = true;
            log.warn("The license clock state could not be recorded anywhere ({} location(s) tried). "
                    + "Clock rollback protection only lasts until the next restart.", stores.size());
        }
    }

    private LocalDate parse(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        int separator = trimmed.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        String epochDay = trimmed.substring(0, separator);
        String mac = trimmed.substring(separator + 1);
        if (!MessageDigest.isEqual(mac.getBytes(StandardCharsets.US_ASCII),
                authenticate(epochDay).getBytes(StandardCharsets.US_ASCII))) {
            return null;
        }
        try {
            return LocalDate.ofEpochDay(Long.parseLong(epochDay));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String recordOf(LocalDate date) {
        String epochDay = Long.toString(date.toEpochDay());
        return epochDay + ':' + authenticate(epochDay);
    }

    private static LocalDate latest(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }

    private String authenticate(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is required by every JRE", e);
        }
    }

    /**
     * Derives the HMAC key from the trust anchor and the machine fingerprint. The public
     * key is not secret, but binding to it plus the fingerprint means a copy is only
     * meaningful for this build on this machine -- it cannot be carried over from another
     * installation to fake an earlier "last seen" date.
     */
    private static byte[] deriveKey(byte[] trustAnchor, String fingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(KEY_CONTEXT);
            digest.update(trustAnchor);
            digest.update(fingerprint.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JRE", e);
        }
    }

    /**
     * The file the guard always had, then copies that do not depend on its configurable
     * path: the database, and the registry on Windows.
     */
    private static List<TrialStore> defaultStores(Path stateFile, Supplier<DataSource> dataSource,
                                                  boolean systemWide) {
        List<TrialStore> stores = new ArrayList<>();
        stores.add(new FileStore(stateFile));
        stores.add(new DatabaseTrialStore(dataSource, DATABASE_KEY));
        if (systemWide && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            stores.add(new WindowsRegistryStore(REGISTRY_VALUE));
        }
        return stores;
    }
}
