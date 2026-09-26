package com.houssen.liberoshop.license;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Grants an evaluation period to an installation that has no license file -- once, and
 * only once.
 *
 * <p>Without this, a missing {@code .lic} simply stopped the boot. That is correct but
 * brutal: a fresh installation could do nothing at all before the publisher had issued a
 * file. The trial fills that gap, and the whole difficulty is making it non-repeatable:
 * if deleting the license folder handed out another 30 free days, the licensing scheme
 * would be decorative.
 *
 * <p>The answer is a small record of the day this installation first ran, written to
 * several independent places at once:
 * <ul>
 *   <li>beside the license file, {@code .license-trial};</li>
 *   <li>in the application's own database;</li>
 *   <li>in the user profile ({@code %LOCALAPPDATA%}, {@code ~/.config}, {@code ~/Library});</li>
 *   <li>in the machine-wide data directory ({@code %ProgramData%}, or {@code /var/lib});</li>
 *   <li>on Windows, in the registry under {@code HKCU\Software\LibertyShop}.</li>
 * </ul>
 *
 * <p>The database copy is the one that matters. Every file and registry location can be
 * found in a couple of minutes with a file monitor and wiped by a three-line script, so
 * replication across directories only raises the cost of erasing them -- it does not
 * change the outcome. The database is different in kind: it is where the shop's own sales,
 * stock and invoices live, so a customer who drops it to reopen a trial pays for it with
 * their own history. That is the only deterrent available to a scheme with no server.
 *
 * <p>Every copy is authenticated with an HMAC keyed on the embedded public key <em>and</em>
 * the machine fingerprint. That binding is what makes tampering visible rather than merely
 * difficult: a hand-written record, or one copied from another machine that is still in its
 * trial, fails its check and is rejected and overwritten instead of being believed.
 *
 * <p>Reading takes the <em>earliest</em> surviving valid record, then repairs every copy
 * that was missing or wrong -- so the trial can only ever be shortened by tampering, never
 * extended, and each removal is logged. The date is recorded on every start, licensed or
 * not, so a customer who deletes a paid license months later does not land in a fresh
 * trial: theirs was consumed on day one.
 *
 * <p>Like {@link ClockGuard}, no failure here is fatal. A read-only directory or an
 * unavailable registry degrades to "one copy fewer", never to a blocked till.
 */
class TrialRegistry {

    private static final Logger log = LoggerFactory.getLogger(TrialRegistry.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Domain separation, so this key can never collide with the clock guard's.
     *
     * <p>Keeps the product's former name on purpose, and must: it authenticates the trial
     * records. Renaming it would invalidate every record already written, and a machine that
     * has consumed its evaluation period would be granted a fresh one -- the one thing this
     * class exists to prevent.
     */
    private static final byte[] KEY_CONTEXT = "liberty-shop/trial/v1".getBytes(StandardCharsets.UTF_8);

    /**
     * Compiled-in salt for the HMAC key.
     *
     * <p>The clock guard derives its key from the public key and the fingerprint alone,
     * which is enough for it: its record cannot lie in a useful direction anyway. A trial
     * record can -- a start date far in the future would mean a trial that never ends --
     * and both of those inputs are public (the key ships in the jar, the fingerprint is
     * printed in the UI). This constant is not. Forging a record therefore requires
     * decompiling the application, which is the level of effort this module has always
     * declared out of scope, rather than reading two values off the screen.
     */
    private static final byte[] KEY_SALT = HexFormat.of().parseHex(
            "5c1f9ab34e70d6218c4f0b93a7e5d2861fb04c79e38a5d6207b9c1e4f83a02d5");

    /** Record layout version, so a future format can be recognised rather than guessed. */
    private static final String RECORD_VERSION = "1";

    /** Identity of the synthetic license handed out during the trial. */
    private static final String TRIAL_CUSTOMER_ID = "TRIAL";
    private static final String TRIAL_CUSTOMER_NAME = "Periode d'essai";

    private final boolean enabled;
    private final int days;
    private final String fingerprint;
    private final byte[] key;
    private final List<TrialStore> stores;

    /**
     * The shop's own sales and invoices, consulted as a witness to the age of this
     * installation. Unlike every store above, it is read and never written: its value
     * comes precisely from being data nobody keeps for the licence's sake.
     */
    private final BusinessDataWitness witness;

    /** Set when the records and the shop's own data disagree; French, it reaches the user. */
    private volatile String integrityWarning;

    /**
     * Resolved start date, cached: resolution shells out to the registry and touches
     * several directories, which is fine once per run but not once per sale.
     */
    private volatile LocalDate start;
    private volatile boolean loaded;

    /**
     * Production wiring: derives the storage locations from the license path and the OS.
     *
     * @param dataSource handle on the application database, resolved lazily. It is a
     *                   {@link Supplier} because the earliest license check runs before any
     *                   bean exists -- there simply is no connection pool yet at that point,
     *                   and the supplier then yields {@code null} and the database copy is
     *                   quietly skipped.
     */
    TrialRegistry(LicenseProperties properties, byte[] trustAnchor, String fingerprint,
                  Supplier<DataSource> dataSource, BusinessDataWitness witness) {
        this(properties.trial().enabled(), properties.trial().days(),
                defaultStores(properties, dataSource), trustAnchor, fingerprint, witness);
    }

    /** Without a witness: the unit tests, and any installation with no database at all. */
    TrialRegistry(boolean enabled, int days, List<TrialStore> stores, byte[] trustAnchor, String fingerprint) {
        this(enabled, days, stores, trustAnchor, fingerprint, BusinessDataWitness.none());
    }

    /**
     * @param enabled     whether an unlicensed installation may run at all
     * @param days        length of the trial, first day included
     * @param stores      where the record is replicated; all of them are read, all repaired
     * @param trustAnchor encoded public key of the build, mixed into the HMAC key
     * @param fingerprint machine fingerprint, mixed into the HMAC key
     * @param witness     the shop's own data, read as a second opinion on the records above
     */
    TrialRegistry(boolean enabled, int days, List<TrialStore> stores, byte[] trustAnchor, String fingerprint,
                  BusinessDataWitness witness) {
        this.enabled = enabled;
        this.days = days;
        this.stores = List.copyOf(stores);
        this.fingerprint = fingerprint;
        this.key = deriveKey(trustAnchor, fingerprint);
        this.witness = witness;
    }

    /**
     * Records that this installation ran today, without granting anything.
     *
     * <p>Called on every refresh, including while a valid license is installed: the trial
     * window has to start ticking on the day of installation, otherwise deleting a paid
     * license a year later would open a brand new one.
     */
    void recordFirstRun(LocalDate today) {
        if (enabled) {
            firstRun(today);
        }
    }

    /**
     * The trial status for a day, or empty when the trial is switched off.
     *
     * @param today the evaluation date, already corrected for clock rollback
     */
    Optional<LicenseStatus> statusOn(LocalDate today) {
        if (!enabled) {
            return Optional.empty();
        }
        License license = licenseFrom(firstRun(today));
        LicenseState state = today.isAfter(license.expiresOn())
                ? LicenseState.TRIAL_EXPIRED
                : LicenseState.TRIAL;
        return Optional.of(new LicenseStatus(license, state, today, fingerprint));
    }

    /** Whether an unlicensed installation is allowed to fall back to a trial at all. */
    boolean isEnabled() {
        return enabled;
    }

    /**
     * The synthetic license standing in for a real one during the trial.
     *
     * <p>Built rather than signed on purpose: it never touches {@link LicenseVerifier}, so
     * no code path exists in which locally produced data could pass for publisher-signed
     * data. It is bound to this machine like any other license, and {@link LicenseState}
     * keeps the two apart everywhere they are displayed or enforced.
     */
    private License licenseFrom(LocalDate begin) {
        return new License(
                "TRIAL-" + fingerprint,
                TRIAL_CUSTOMER_ID,
                TRIAL_CUSTOMER_NAME,
                LicensePlan.TRIAL,
                begin,
                begin.plusDays(days - 1L),
                0,
                List.of(fingerprint),
                "Periode d'essai de " + days + " jours, accordee une seule fois par machine.");
    }

    private LocalDate firstRun(LocalDate today) {
        if (loaded) {
            return start;
        }
        synchronized (this) {
            if (!loaded) {
                start = resolve(today);
                loaded = true;
            }
        }
        return start;
    }

    /** What the records and the shop's own data disagreed about, if anything. */
    Optional<String> integrityWarning() {
        return Optional.ofNullable(integrityWarning);
    }

    /**
     * Reads every copy, keeps the earliest trustworthy one and repairs the rest.
     *
     * <p>Earliest rather than latest: a record that has been removed or rewritten must
     * never be able to postpone the end of the trial.
     *
     * <p>The shop's own data has a vote here, and it is the vote that cannot be bought
     * cheaply. Erasing every marker makes this installation look new; it does not make
     * eight months of sales disappear, and those are consulted alongside the markers. What
     * the two disagree about is recorded in {@link #integrityWarning} and shown to the
     * user -- because a customer whose database is being edited behind their back has a
     * problem worth hearing about, whoever is doing it.
     */
    private LocalDate resolve(LocalDate today) {
        List<LocalDate> readings = new ArrayList<>(stores.size());
        LocalDate earliest = null;
        int absent = 0;
        int rejected = 0;
        int intact = 0;
        int silent = 0;

        for (TrialStore store : stores) {
            // Read once and keep the raw content: telling "absent" from "altered" is the
            // whole point of the log line below, and a registry read is a process launch.
            String raw = store.read();
            LocalDate found = parse(raw);
            readings.add(found);
            if (found == null) {
                if (raw == null || raw.isBlank()) {
                    // A database that has just been created, restored or is simply
                    // unreachable is not evidence of anything; a deleted file is.
                    if (store.absenceIsSuspicious()) {
                        absent++;
                    } else {
                        silent++;
                    }
                } else {
                    rejected++;
                    log.warn("Trial record at {} failed its integrity check for this machine and was ignored.",
                            store.describe());
                }
            } else {
                intact++;
                if (earliest == null || found.isBefore(earliest)) {
                    earliest = found;
                }
            }
        }

        // The shop's own history, which nobody keeps for the licence's sake. It only ever
        // pulls the start date backwards -- it is a lower bound on when this installation
        // was already working, so it can shorten a trial and never extend one.
        LocalDate provenSince = witness.provenActiveSince(today);
        boolean contradicted = provenSince != null && (earliest == null || provenSince.isBefore(earliest));
        if (contradicted) {
            earliest = provenSince;
        }

        // A start date in the future would describe a trial that never ends. It cannot
        // happen honestly -- the evaluation date never moves backwards, the clock guard
        // sees to that -- so it is clamped to today and rewritten. The worst a forged
        // record can buy is therefore one more trial, exactly like erasing the records.
        boolean postDated = earliest != null && earliest.isAfter(today);
        if (postDated) {
            log.warn("Trial record dated {} lies in the future and was clamped to {}.", earliest, today);
        }
        boolean firstEver = earliest == null;
        LocalDate resolved = firstEver || postDated ? today : earliest;

        for (int i = 0; i < stores.size(); i++) {
            if (!resolved.equals(readings.get(i))) {
                stores.get(i).write(record(resolved));
            }
        }

        if (contradicted) {
            // The records say one thing, the shop's own till roll says another. Reported
            // as its own line, and to the user rather than only to the log: on a machine
            // where someone is editing the database directly, the owner is usually the
            // last to know.
            long operations = witness.read().rows();
            log.warn("Installation age contradicted: {} intact trial record(s) claimed a newer "
                            + "installation, but {} business operation(s) are on file from {} onwards. "
                            + "The evaluation period is counted from the data.",
                    intact, operations, provenSince);
            integrityWarning = "Incoherence detectee : " + operations + " operation(s) (ventes, factures, "
                    + "mouvements de stock) sont enregistrees sur ce poste depuis le " + provenSince
                    + ", alors que les reperes d'installation le presentent comme neuf. La periode "
                    + "d'essai est donc decomptee depuis cette date. Si vous n'etes pas a l'origine "
                    + "de cette modification, contactez l'editeur : toute intervention directe dans "
                    + "la base de donnees risque de vous faire perdre vos ventes et votre stock.";
        }

        if (firstEver) {
            log.info("No trial record found: starting the {}-day evaluation period on {}.", days, resolved);
        } else if (absent > 0 || rejected > 0) {
            // The interesting line for support: it says out loud that someone went
            // looking for the license files.
            log.warn("Trial records tampered with: {} missing, {} altered, {} intact. "
                            + "The evaluation period still starts on {} and has been restored everywhere.",
                    absent, rejected, intact, resolved);
        } else {
            log.debug("Trial period started on {}.", resolved);
        }
        if (silent > 0) {
            // Reported apart from the tamper line so it never raises a false alarm: a
            // development database is in-memory and starts empty on every run.
            log.debug("{} trial store(s) held no record without that meaning anything.", silent);
        }
        return resolved;
    }

    /** The date held by one copy, or {@code null} when it is absent or not authentic. */
    private LocalDate parse(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String[] parts = content.trim().split(":");
        if (parts.length != 3 || !RECORD_VERSION.equals(parts[0])) {
            return null;
        }
        if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.US_ASCII),
                authenticate(parts[0] + ':' + parts[1]).getBytes(StandardCharsets.US_ASCII))) {
            return null;
        }
        try {
            return LocalDate.ofEpochDay(Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String record(LocalDate date) {
        String body = RECORD_VERSION + ':' + date.toEpochDay();
        return body + ':' + authenticate(body);
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
     * Ties the record to this build and this machine. The public key is not a secret, but
     * mixing it with the fingerprint means a record is only meaningful where it was
     * written -- it cannot be carried over from a colleague's machine that is still inside
     * its own trial.
     */
    private static byte[] deriveKey(byte[] trustAnchor, String fingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(KEY_CONTEXT);
            digest.update(KEY_SALT);
            digest.update(trustAnchor);
            digest.update(fingerprint.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JRE", e);
        }
    }

    // ------------------------------------------------------------------
    // Storage locations
    // ------------------------------------------------------------------

    /**
     * The places a record is kept: the application database, plus directories with
     * different owners and, on Windows, the registry.
     *
     * <p>The database is added first and unconditionally -- it is the same store on every
     * operating system, and the only one whose removal costs the customer something. The
     * directories that follow are per-platform and deliberately spread out, so that
     * erasing one is an accident and erasing all of them is a decision.
     */
    private static List<TrialStore> defaultStores(LicenseProperties properties, Supplier<DataSource> dataSource) {
        List<TrialStore> stores = new ArrayList<>();
        stores.add(new FileStore(properties.resolvedTrialPath()));
        stores.add(new DatabaseTrialStore(dataSource));
        if (!properties.trial().systemWide()) {
            return stores;
        }
        String home = System.getProperty("user.home");
        Set<Path> seen = new LinkedHashSet<>();
        if (isWindows()) {
            addFileStore(stores, seen, System.getenv("LOCALAPPDATA"));
            addFileStore(stores, seen, System.getenv("ProgramData"));
            stores.add(new WindowsRegistryStore());
        } else if (isMac()) {
            addFileStore(stores, seen, home);
            addFileStore(stores, seen, home == null ? null : home + "/Library/Application Support");
            addFileStore(stores, seen, "/usr/local/var");
        } else {
            // The historical location is kept alongside the XDG ones rather than replaced:
            // an installation that already recorded its first run there must not lose it.
            addFileStore(stores, seen, home);
            addFileStore(stores, seen, xdg("XDG_CONFIG_HOME", home, "/.config"));
            addFileStore(stores, seen, xdg("XDG_DATA_HOME", home, "/.local/share"));
            addFileStore(stores, seen, "/var/lib");
        }
        return stores;
    }

    /** An XDG base directory, honouring the environment variable when the desktop sets one. */
    private static String xdg(String variable, String home, String fallback) {
        String configured = System.getenv(variable);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return home == null ? null : home + fallback;
    }

    /** Adds a location, skipping blanks and any path a previous candidate already resolved to. */
    private static void addFileStore(List<TrialStore> stores, Set<Path> seen, String parent) {
        if (parent == null || parent.isBlank()) {
            return;
        }
        Path path;
        try {
            // Former product name, kept deliberately: this is where the markers of every
            // installation already out there live. Looking somewhere else would find none of
            // them and start a second evaluation period on a machine that used its own.
            path = Path.of(parent, "LibertyShop", ".trial").toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            // An environment variable holding something that is not a path at all.
            return;
        }
        if (seen.add(path)) {
            stores.add(new FileStore(path));
        }
    }

    private static boolean isWindows() {
        return osName().contains("win");
    }

    private static boolean isMac() {
        String os = osName();
        return os.contains("mac") || os.contains("darwin");
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    /** One place a record can be kept. Every operation is best effort and never throws. */
    interface TrialStore {

        /** Human-readable location, for the log line that reports tampering. */
        String describe();

        /** The stored record, or {@code null} when there is none. */
        String read();

        /** Stores a record, creating whatever is needed. Silently gives up if it cannot. */
        void write(String record);

        /**
         * Whether finding nothing here is worth reporting as tampering.
         *
         * <p>True for a file or a registry value: nobody deletes one by accident. False for
         * the database, which is legitimately empty on a first run, after a restore from
         * backup, after a schema migration, or on every single start in development where
         * it lives in memory. Folding those into the tamper count would make the warning
         * fire so often that nobody would read it the day it matters.
         */
        default boolean absenceIsSuspicious() {
            return true;
        }
    }

    /** A hidden file. The workhorse: three of the four default locations are these. */
    static final class FileStore implements TrialStore {

        private final Path path;

        FileStore(Path path) {
            this.path = path;
        }

        @Override
        public String describe() {
            return path.toString();
        }

        @Override
        public String read() {
            try {
                return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.US_ASCII) : null;
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        @Override
        public void write(String record) {
            try {
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.writeString(path, record, StandardCharsets.US_ASCII);
                hide();
            } catch (IOException | RuntimeException e) {
                // A read-only or protected directory (ProgramData often is): one copy fewer.
                log.debug("Could not write the trial record at {}: {}", path, e.toString());
            }
        }

        /** Keeps the record out of the customer's file explorer. Best effort. */
        private void hide() {
            try {
                if (isWindows()) {
                    Files.setAttribute(path, "dos:hidden", Boolean.TRUE);
                }
            } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
                // Not a supported attribute view: harmless.
            }
        }
    }

    /**
     * A registry value under {@code HKCU\Software\LibertyShop}.
     *
     * <p>Worth the shell-out: it is the one location that survives deleting the whole
     * installation folder <em>and</em> clearing the data directories, and the one a
     * customer is least likely to think of. {@code reg.exe} is used rather than the JDK
     * preferences API, which would silently pick its own key path and log warnings when
     * writing is refused.
     */
    static final class WindowsRegistryStore implements TrialStore {

        /** Former product name, for the same reason as the file stores above. */
        private static final String KEY_PATH = "HKCU\\Software\\LibertyShop";
        private static final String VALUE_NAME = "InstallMarker";

        @Override
        public String describe() {
            return KEY_PATH + '\\' + VALUE_NAME;
        }

        @Override
        public String read() {
            String output = MachineFingerprint.runCommand(
                    List.of("reg", "query", KEY_PATH, "/v", VALUE_NAME));
            if (output == null) {
                return null;
            }
            for (String line : output.split("\\R")) {
                int type = line.indexOf("REG_SZ");
                if (line.contains(VALUE_NAME) && type >= 0) {
                    String value = line.substring(type + "REG_SZ".length()).trim();
                    return value.isEmpty() ? null : value;
                }
            }
            return null;
        }

        @Override
        public void write(String record) {
            MachineFingerprint.runCommand(
                    List.of("reg", "add", KEY_PATH, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", record, "/f"));
        }
    }

    /**
     * A row in the application's own database -- the anchor the other stores cannot be.
     *
     * <p>Files and registry values are cheap to find: a file monitor shows every path the
     * application touches on its first start, so the four original locations are really one
     * location that takes four lines to erase instead of one. The database cannot be
     * neutralised the same way. It is where the shop's sales, stock and invoices live, so
     * dropping it to get another evaluation period costs the customer their own history --
     * which is a price no script can pay for them. The record is also the last thing a
     * reinstall of the application removes, since the database usually outlives the
     * installation directory entirely.
     *
     * <p>It carries the very same HMAC-authenticated record as the files, so the two are
     * mutually verifiable: a row lifted from a colleague's database fails on this machine
     * exactly as a copied file does, and neither can postpone what the other already knows.
     *
     * <p>The table is created on demand rather than mapped as an entity. A JPA entity would
     * tie the check to Hibernate's lifecycle and show up in the schema the customer
     * browses; plain JDBC against a table this class owns keeps it independent of whatever
     * the rest of the application does with its mappings.
     *
     * <p>Every statement is portable across the three engines on the classpath (H2, MySQL,
     * PostgreSQL): {@code CREATE TABLE IF NOT EXISTS} is understood by all of them, and the
     * write is an {@code UPDATE} followed by an {@code INSERT} when nothing was updated,
     * rather than a dialect-specific upsert.
     */
    static final class DatabaseTrialStore implements TrialStore {

        private static final String TABLE = "ls_install_marker";
        private static final String MARKER_KEY = "trial.first-run";

        private static final String CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "marker_key VARCHAR(64) NOT NULL PRIMARY KEY, "
                + "marker_value VARCHAR(255) NOT NULL)";
        private static final String SELECT = "SELECT marker_value FROM " + TABLE + " WHERE marker_key = ?";
        private static final String UPDATE = "UPDATE " + TABLE + " SET marker_value = ? WHERE marker_key = ?";
        private static final String INSERT = "INSERT INTO " + TABLE + " (marker_key, marker_value) VALUES (?, ?)";

        private final Supplier<DataSource> dataSource;

        DatabaseTrialStore(Supplier<DataSource> dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public String describe() {
            return "database table " + TABLE;
        }

        /** Never suspicious: an empty database is the normal state of a fresh install. */
        @Override
        public boolean absenceIsSuspicious() {
            return false;
        }

        @Override
        public String read() {
            try (Connection connection = open()) {
                if (connection == null) {
                    return null;
                }
                ensureTable(connection);
                try (PreparedStatement statement = connection.prepareStatement(SELECT)) {
                    statement.setString(1, MARKER_KEY);
                    try (ResultSet rows = statement.executeQuery()) {
                        return rows.next() ? rows.getString(1) : null;
                    }
                }
            } catch (SQLException | RuntimeException e) {
                // No pool yet, no schema rights, a database that is still starting: this is
                // one copy fewer, never a reason to stop the till.
                log.debug("Could not read the trial record from the database: {}", e.toString());
                return null;
            }
        }

        @Override
        public void write(String record) {
            try (Connection connection = open()) {
                if (connection == null) {
                    return;
                }
                ensureTable(connection);
                try (PreparedStatement update = connection.prepareStatement(UPDATE)) {
                    update.setString(1, record);
                    update.setString(2, MARKER_KEY);
                    if (update.executeUpdate() > 0) {
                        return;
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
                    insert.setString(1, MARKER_KEY);
                    insert.setString(2, record);
                    insert.executeUpdate();
                }
            } catch (SQLException | RuntimeException e) {
                log.debug("Could not write the trial record to the database: {}", e.toString());
            }
        }

        private Connection open() throws SQLException {
            DataSource resolved = dataSource == null ? null : dataSource.get();
            return resolved == null ? null : resolved.getConnection();
        }

        private static void ensureTable(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE);
            }
        }
    }
}
