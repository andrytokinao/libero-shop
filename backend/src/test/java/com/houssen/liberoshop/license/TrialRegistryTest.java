package com.houssen.liberoshop.license;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trial is only worth having if it cannot be taken twice. These tests are all about
 * that one property: what survives a customer deleting, editing or importing the records.
 *
 * <p>The stores are plain files in temporary directories, which is what most of the
 * production locations are, plus a throwaway in-memory database for the one store whose
 * behaviour is not shared with the others. The Windows registry copy is not exercised here
 * -- it is the same interface with a different back end, and a test that wrote to the real
 * {@code HKCU} of the build machine would be worse than no test.
 */
class TrialRegistryTest {

    private static final byte[] TRUST_ANCHOR = "test-trust-anchor".getBytes(StandardCharsets.UTF_8);
    private static final String FINGERPRINT = TestLicenses.DEFAULT_FINGERPRINT;
    private static final String OTHER_MACHINE = "LS1-ZZZZZ-99999-YYYYY-88888";
    private static final LocalDate FIRST_RUN = LocalDate.of(2026, 6, 1);
    private static final int DAYS = 30;

    @TempDir
    Path installDirectory;
    @TempDir
    Path userProfile;
    @TempDir
    Path machineWide;

    private List<Path> records;

    @BeforeEach
    void setUp() {
        records = List.of(
                installDirectory.resolve(".license-trial"),
                userProfile.resolve("LibertyShop/.trial"),
                machineWide.resolve("LibertyShop/.trial"));
    }

    @Test
    @DisplayName("starts the trial on the first run and writes every copy")
    void startsTheTrialAndReplicatesIt() {
        LicenseStatus status = registry(records).statusOn(FIRST_RUN).orElseThrow();

        assertEquals(LicenseState.TRIAL, status.state());
        assertEquals(FIRST_RUN, status.license().issuedOn());
        assertEquals(FIRST_RUN.plusDays(DAYS - 1), status.license().expiresOn());
        records.forEach(record -> assertTrue(Files.isRegularFile(record), record + " should have been written"));
    }

    @Test
    @DisplayName("ends the trial after the configured number of days")
    void endsAfterItsDays() {
        registry(records).statusOn(FIRST_RUN);

        assertEquals(LicenseState.TRIAL, registry(records).statusOn(FIRST_RUN.plusDays(29)).orElseThrow().state());
        assertEquals(LicenseState.TRIAL_EXPIRED,
                registry(records).statusOn(FIRST_RUN.plusDays(30)).orElseThrow().state());
    }

    @Test
    @DisplayName("restores the records a customer deleted, from the copies they missed")
    void restoresDeletedRecords() throws IOException {
        registry(records).statusOn(FIRST_RUN);
        Files.delete(records.get(0));
        Files.delete(records.get(1));

        LicenseStatus status = registry(records).statusOn(FIRST_RUN.plusDays(40)).orElseThrow();

        assertEquals(LicenseState.TRIAL_EXPIRED, status.state(),
                "emptying the license folder must not hand out another trial");
        assertFalse(status.writesAllowed());
        records.forEach(record -> assertTrue(Files.isRegularFile(record), record + " should have been restored"));
    }

    @Test
    @DisplayName("keeps the earliest record and realigns the others")
    void keepsTheEarliestRecord() throws IOException {
        registry(records).statusOn(FIRST_RUN);

        // One copy is made to claim a much later start, the way a customer would after
        // deleting it and letting the application recreate it on its own.
        Files.delete(records.get(2));
        registry(List.of(records.get(2))).statusOn(FIRST_RUN.plusDays(20));

        LicenseStatus status = registry(records).statusOn(FIRST_RUN.plusDays(25)).orElseThrow();

        assertEquals(FIRST_RUN, status.license().issuedOn(), "the earliest record decides");
        assertEquals(FIRST_RUN, registry(List.of(records.get(2))).statusOn(FIRST_RUN.plusDays(25))
                .orElseThrow().license().issuedOn(), "the late copy should have been realigned");
    }

    @Test
    @DisplayName("ignores a hand-edited record and rewrites it")
    void ignoresAHandEditedRecord() throws IOException {
        registry(records).statusOn(FIRST_RUN);
        String authentic = Files.readString(records.get(0), StandardCharsets.US_ASCII);
        // A far-future start with a plausible but wrong HMAC: the trial that never ends.
        Files.writeString(records.get(0), "1:" + LocalDate.of(2099, 1, 1).toEpochDay() + ":00ff00ff");

        LicenseStatus status = registry(records).statusOn(FIRST_RUN.plusDays(40)).orElseThrow();

        assertEquals(LicenseState.TRIAL_EXPIRED, status.state());
        assertEquals(authentic, Files.readString(records.get(0), StandardCharsets.US_ASCII),
                "the altered record should have been rewritten from the surviving copies");
    }

    @Test
    @DisplayName("clamps a post-dated record instead of granting an endless trial")
    void clampsAPostDatedRecord() {
        // Every copy claims a start in 2099 -- what someone who found the key would write.
        registry(records).statusOn(LocalDate.of(2099, 1, 1));

        LicenseStatus status = registry(records).statusOn(FIRST_RUN).orElseThrow();

        assertEquals(FIRST_RUN, status.license().issuedOn(), "a future start date buys nothing");
        assertEquals(FIRST_RUN.plusDays(DAYS - 1), status.license().expiresOn());
    }

    @Test
    @DisplayName("refuses a record copied from another machine")
    void refusesAForeignRecord() {
        // Records written on, and authenticated for, a different machine.
        registry(records, OTHER_MACHINE).statusOn(FIRST_RUN);

        LicenseStatus status = registry(records).statusOn(FIRST_RUN.plusDays(40)).orElseThrow();

        // Had the foreign record been adopted, the trial would read as started on
        // FIRST_RUN and already over. It is discarded instead, and this machine's own
        // record is written in its place.
        assertEquals(FIRST_RUN.plusDays(40), status.license().issuedOn());
        assertEquals(LicenseState.TRIAL, status.state());
        assertEquals(FINGERPRINT, status.fingerprint());
    }

    @Test
    @DisplayName("grants nothing when the trial is switched off")
    void grantsNothingWhenDisabled() {
        TrialRegistry registry = new TrialRegistry(false, DAYS, stores(records), TRUST_ANCHOR, FINGERPRINT);

        assertTrue(registry.statusOn(FIRST_RUN).isEmpty());
        assertFalse(registry.isEnabled());
        records.forEach(record -> assertFalse(Files.exists(record), "nothing should have been written"));
    }

    // ------------------------------------------------------------------
    // The database copy
    // ------------------------------------------------------------------

    @Test
    @DisplayName("keeps the trial when every file was erased and only the database row is left")
    void survivesOnTheDatabaseAlone() throws IOException {
        DataSource database = inMemoryDatabase();
        registry(records, database, FINGERPRINT).statusOn(FIRST_RUN);
        eraseEveryFile();

        LicenseStatus status = registry(records, database, FINGERPRINT)
                .statusOn(FIRST_RUN.plusDays(DAYS + 10)).orElseThrow();

        // This is the property the file copies cannot provide on their own: they can all
        // be located with a file monitor and deleted by a script, the database cannot.
        assertEquals(FIRST_RUN, status.license().issuedOn());
        assertEquals(LicenseState.TRIAL_EXPIRED, status.state(),
                "wiping every file must not hand out another trial while the database remembers");
        records.forEach(record -> assertTrue(Files.isRegularFile(record), record + " should have been restored"));
    }

    @Test
    @DisplayName("refuses a database row written for another machine")
    void refusesAForeignDatabaseRow() throws IOException {
        // A database dump lifted from a machine that is still inside its own trial.
        DataSource database = inMemoryDatabase();
        registry(records, database, OTHER_MACHINE).statusOn(FIRST_RUN);
        eraseEveryFile();

        LicenseStatus status = registry(records, database, FINGERPRINT)
                .statusOn(FIRST_RUN.plusDays(DAYS + 10)).orElseThrow();

        assertEquals(LicenseState.TRIAL, status.state(), "a foreign row must be discarded, not believed");
        assertEquals(FIRST_RUN.plusDays(DAYS + 10), status.license().issuedOn());
    }

    @Test
    @DisplayName("writes the date into a database that was recreated empty")
    void repairsARecreatedDatabase() throws IOException {
        registry(records, inMemoryDatabase(), FINGERPRINT).statusOn(FIRST_RUN);

        // A restore from backup, a migration, or simply a fresh container: the row is gone
        // but the files still hold the date, and the rebuilt database must pick it back up.
        DataSource rebuilt = inMemoryDatabase();
        registry(records, rebuilt, FINGERPRINT).statusOn(FIRST_RUN.plusDays(5));
        eraseEveryFile();

        LicenseStatus status = registry(records, rebuilt, FINGERPRINT)
                .statusOn(FIRST_RUN.plusDays(DAYS + 10)).orElseThrow();

        assertEquals(FIRST_RUN, status.license().issuedOn(), "the repaired row should carry the original date");
        assertEquals(LicenseState.TRIAL_EXPIRED, status.state());
    }

    @Test
    @DisplayName("runs normally on an installation that has no database")
    void worksWithoutADatabase() {
        // The earliest check runs before any connection pool exists, and a deployment may
        // have no database configured at all. Neither is a reason to fail.
        LicenseStatus status = registry(records, null, FINGERPRINT).statusOn(FIRST_RUN).orElseThrow();

        assertEquals(LicenseState.TRIAL, status.state());
        records.forEach(record -> assertTrue(Files.isRegularFile(record), record + " should have been written"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void eraseEveryFile() throws IOException {
        for (Path record : records) {
            Files.deleteIfExists(record);
        }
    }

    private static TrialRegistry registry(List<Path> records) {
        return registry(records, FINGERPRINT);
    }

    private static TrialRegistry registry(List<Path> records, String fingerprint) {
        return new TrialRegistry(true, DAYS, stores(records), TRUST_ANCHOR, fingerprint);
    }

    /** @param database may be {@code null}, standing for an installation with no pool yet */
    private static TrialRegistry registry(List<Path> records, DataSource database, String fingerprint) {
        List<TrialRegistry.TrialStore> stores = stores(records);
        stores.add(new TrialRegistry.DatabaseTrialStore(() -> database));
        return new TrialRegistry(true, DAYS, stores, TRUST_ANCHOR, fingerprint);
    }

    private static List<TrialRegistry.TrialStore> stores(List<Path> records) {
        List<TrialRegistry.TrialStore> stores = new ArrayList<>(records.size());
        records.forEach(record -> stores.add(new TrialRegistry.FileStore(record)));
        return stores;
    }

    /**
     * A private in-memory database, reached through {@link DriverManager} so that no H2
     * class is named here: the driver is a runtime dependency and is deliberately not on
     * the test compile path. {@code DB_CLOSE_DELAY=-1} keeps it alive between connections,
     * which is what makes it stand in for a database that outlives a restart.
     */
    private static DataSource inMemoryDatabase() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:trial-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    /** The few {@link DataSource} methods the store actually calls; the rest are refused. */
    private record DriverManagerDataSource(String url) implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> type) {
            return false;
        }
    }
}
