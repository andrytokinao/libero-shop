package com.houssen.liberoshop.license;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * What the shop's own data says about the age of this installation.
 *
 * <p>Every other record this module keeps -- the files, the registry value, the row in
 * {@code ls_install_marker} -- exists only to serve the licence, and that is precisely
 * their weakness. A table whose single purpose is to remember a date is a table with
 * nothing in it the customer wants: Process Monitor names it in three minutes, one
 * {@code DELETE} empties it, and the evaluation period reopens. Replicating such a record
 * across five locations turns a one-line script into a four-line one and changes nothing
 * else.
 *
 * <p>This class reads something that cannot be emptied so cheaply: the sales, invoices,
 * payments, stock movements and cash remittances the shop has been recording all along.
 * Their dates are a by-product of the business, not of the licence, and they say things
 * the licence records cannot say on their own:
 *
 * <ul>
 *   <li><b>How old this installation really is.</b> The oldest operation on file is a
 *       lower bound on the first run. Wiping every marker to claim a fresh installation
 *       leaves eight months of sales sitting there contradicting the claim.</li>
 *   <li><b>Whether the clock was wound back.</b> An operation recorded <em>after</em>
 *       today cannot have happened, so the system date is behind where it has already
 *       been -- and unlike {@code ClockGuard}, this needs no state file that could be
 *       deleted first.</li>
 * </ul>
 *
 * <p>The point is not that these rows are hidden -- they are the most visible thing in the
 * database. It is that erasing them costs the customer their own history, which is a price
 * no script pays on their behalf. That asymmetry is the only real deterrent available to a
 * scheme with no server, and it is the same one the database marker was already reaching
 * for; this simply stops depending on a table whose deletion is free.
 *
 * <p>Read-only by construction: {@code MIN}, {@code MAX} and {@code COUNT} and nothing
 * else. Like everything in this module, no failure here is fatal -- a table that does not
 * exist yet, a schema still being created by Hibernate, or no pool at all simply means one
 * witness fewer, never a blocked till.
 */
final class BusinessDataWitness {

    private static final Logger log = LoggerFactory.getLogger(BusinessDataWitness.class);

    /**
     * How much older than today the data must be before it is taken as evidence.
     *
     * <p>Not zero, and the reason is a legitimate case rather than a hostile one. Demo
     * data seeds operations up to five days back, a restore from last night's backup
     * carries yesterday's sales, and a migration can rewrite timestamps by a few hours.
     * None of those describe an installation that has been running for months, and
     * treating them as such would shorten a genuine trial for no reason. The behaviour
     * this exists to catch -- months of history against a marker claiming a fresh install
     * -- clears a week by a wide margin.
     */
    private static final int MATERIAL_AGE_DAYS = 7;

    /**
     * The tables consulted, with the column that dates each row.
     *
     * <p>Chosen for being the ones a shop cannot do without: deleting all of them is not a
     * workaround, it is the loss of the sales history, the invoices and the stock ledger.
     * Reference data is deliberately left out -- a product catalogue can be re-imported
     * from a supplier's file, so its age proves nothing about how long the till has run.
     */
    private static final List<Source> SOURCES = List.of(
            new Source("sale", "sale_date"),
            new Source("invoice", "invoice_date"),
            new Source("payment", "payment_date"),
            new Source("stock_movement", "movement_date"),
            new Source("cash_remittance", "remittance_date"));

    /**
     * How long a silent answer is kept before asking again.
     *
     * <p>A real answer is cached for the run -- five aggregate queries are nothing once
     * per start and a great deal once per sale. Silence is a different matter and must
     * <em>not</em> be cached that way: the earliest checks run before the connection pool
     * exists, and Hibernate may not have created the schema yet when this bean is built.
     * Remembering "the database said nothing" from that moment would disable the witness
     * for the entire run, which is exactly the outcome someone tampering with the
     * installation would want.
     */
    private static final long SILENCE_RETRY_MS = 5 * 60 * 1000L;

    private final Supplier<DataSource> dataSource;

    private volatile Reading reading = Reading.SILENT;
    private volatile long silentUntil;

    BusinessDataWitness(Supplier<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    /** A witness with nothing to say: no database configured, and the unit tests. */
    static BusinessDataWitness none() {
        return new BusinessDataWitness(() -> null);
    }

    /**
     * What the business tables hold.
     *
     * @param earliest the oldest operation on file, or {@code null} when there is none
     * @param latest   the most recent one, or {@code null}
     * @param rows     how many operations were counted across every table
     * @param tables   how many tables actually answered; zero means the database said
     *                 nothing at all and no conclusion may be drawn from it
     */
    record Reading(LocalDate earliest, LocalDate latest, long rows, int tables) {

        static final Reading SILENT = new Reading(null, null, 0, 0);

        boolean hasEvidence() {
            return earliest != null && rows > 0;
        }
    }

    Reading read() {
        Reading current = reading;
        if (current.tables() > 0 || System.currentTimeMillis() < silentUntil) {
            return current;
        }
        synchronized (this) {
            if (reading.tables() == 0 && System.currentTimeMillis() >= silentUntil) {
                reading = query();
                silentUntil = System.currentTimeMillis() + SILENCE_RETRY_MS;
            }
        }
        return reading;
    }

    /**
     * The date this installation can be proven to have been running by, or {@code null}
     * when the data is too recent to prove anything.
     *
     * <p>Answers a lower bound, never an exact first run: the shop may well have been
     * installed before its first sale. That direction is the safe one -- the worst this
     * can do is grant a few more days of trial than were strictly due, where the opposite
     * error would cut short an honest customer's evaluation.
     */
    LocalDate provenActiveSince(LocalDate today) {
        Reading current = read();
        if (!current.hasEvidence()) {
            return null;
        }
        return current.earliest().isBefore(today.minusDays(MATERIAL_AGE_DAYS)) ? current.earliest() : null;
    }

    /**
     * Raises a date to the last day the shop is known to have been working.
     *
     * <p>An invoice cannot have been issued tomorrow. So if the newest one on file is
     * dated after the day being evaluated, the system clock is behind a date this
     * installation has already lived through, and the later of the two is the honest
     * answer.
     *
     * <p>This is the same job {@link ClockGuard} does, done from a different source on
     * purpose. The clock guard keeps its high-water mark in one file, and a file can be
     * deleted before the clock is moved -- the module documents that as a known gap. This
     * one has no file to delete: closing it means deleting the invoices that prove it.
     */
    LocalDate notBefore(LocalDate date) {
        LocalDate latest = read().latest();
        return latest != null && latest.isAfter(date) ? latest : date;
    }

    private Reading query() {
        DataSource resolved = dataSource == null ? null : dataSource.get();
        if (resolved == null) {
            // The earliest check runs before any bean exists, so there is no pool yet.
            return Reading.SILENT;
        }
        LocalDate earliest = null;
        LocalDate latest = null;
        long rows = 0;
        int tables = 0;
        try (Connection connection = resolved.getConnection()) {
            for (Source source : SOURCES) {
                Reading one = source.read(connection);
                if (one == null) {
                    continue;
                }
                tables++;
                rows += one.rows();
                if (one.earliest() != null && (earliest == null || one.earliest().isBefore(earliest))) {
                    earliest = one.earliest();
                }
                if (one.latest() != null && (latest == null || one.latest().isAfter(latest))) {
                    latest = one.latest();
                }
            }
        } catch (SQLException | RuntimeException e) {
            log.debug("Could not consult the business tables: {}", e.toString());
            return Reading.SILENT;
        }
        if (tables == 0) {
            log.debug("No business table answered; the age of this installation stays unknown.");
            return Reading.SILENT;
        }
        log.debug("Business data: {} row(s) across {} table(s), from {} to {}.", rows, tables, earliest, latest);
        return new Reading(earliest, latest, rows, tables);
    }

    /**
     * One table and the column that dates its rows.
     *
     * <p>Both names are compiled-in constants, never anything a request carries, so
     * building the statement as text is safe -- an aggregate over an identifier cannot be
     * parameterised in JDBC anyway.
     */
    private record Source(String table, String column) {

        /** This table's contribution, or {@code null} when it could not be read at all. */
        Reading read(Connection connection) {
            String sql = "SELECT MIN(" + column + "), MAX(" + column + "), COUNT(*) FROM " + table;
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(sql)) {
                if (!rows.next()) {
                    return null;
                }
                return new Reading(dateOf(rows, 1), dateOf(rows, 2), rows.getLong(3), 1);
            } catch (SQLException | RuntimeException e) {
                // A schema Hibernate has not created yet, a table dropped by hand, a
                // renamed column after a migration: one witness fewer, nothing more.
                log.debug("Business table {} could not be read: {}", table, e.toString());
                return null;
            }
        }

        private static LocalDate dateOf(ResultSet rows, int index) throws SQLException {
            Timestamp value = rows.getTimestamp(index);
            return value == null ? null : value.toLocalDateTime().toLocalDate();
        }
    }
}
