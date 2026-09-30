package com.houssen.liberoshop.bootstrap;

import com.houssen.liberoshop.entity.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Brings an installation created before the depot cash had statuses of its own up to date.
 *
 * <p>Two things {@code ddl-auto=update} does not do. It never alters an existing column, and on
 * H2 an enum is stored as a native {@code ENUM('PAID', 'UNPAID')} that refuses any other value:
 * the first hand-over after the upgrade would fail. And it never touches data: the orders already
 * paid to a storekeeper were marked {@code PAID}, though their cash may not have reached the till.
 *
 * <p>The second is told apart by the payment's time. A sale paid at the desk writes its payment at
 * the very instant of the sale; one paid at the depot, at the later hand-over. So a {@code PAID}
 * order whose payment came after the sale and is not in a confirmed slip is really
 * {@code COLLECTED} (no slip yet) or {@code REMITTED} (slip pending). Both updates are
 * idempotent, and find nothing once the data is right -- the application no longer writes
 * {@code PAID} for such an order.
 *
 * <p>Runs after {@link UserRoleMigration} and before {@link DataInitializer}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PaymentStatusMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatusMigration.class);

    private static final List<String> TABLES = List.of("invoice", "sale");
    private static final String COLUMN = "payment_status";

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public PaymentStatusMigration(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String table : TABLES) {
            widenIfNeeded(table);
        }
        reclassifyDepotCash();
    }

    /** Adds the missing constants to a native enum column; leaves any other type alone. */
    private void widenIfNeeded(String table) {
        String type = columnType(table);
        if (type == null || !type.toUpperCase(Locale.ROOT).startsWith("ENUM")) {
            return;
        }
        boolean complete = Arrays.stream(PaymentStatus.values())
                .allMatch(status -> type.contains("'" + status.name() + "'"));
        if (complete) {
            return;
        }
        String values = Arrays.stream(PaymentStatus.values())
                .map(status -> "'" + status.name() + "'")
                .collect(Collectors.joining(", "));
        jdbc.execute("alter table " + table + " alter column " + COLUMN + " set data type enum(" + values + ")");
        log.info("Colonne {}.{} elargie aux statuts {}.", table, COLUMN, values);
    }

    private void reclassifyDepotCash() {
        int collected = reclassify(PaymentStatus.COLLECTED, "p.cash_remittance_id is null");
        int remitted = reclassify(PaymentStatus.REMITTED, """
                exists (select 1 from cash_remittance r
                        where r.id = p.cash_remittance_id and r.status = 'PENDING')""");
        if (collected + remitted > 0) {
            log.info("Especes du depot reclassees : {} commande(s) encaissee(s) au depot, "
                    + "{} versee(s) en attente de confirmation.", collected, remitted);
        }
    }

    /** Moves the PAID orders settled at the depot whose payment matches {@code condition}. */
    private int reclassify(PaymentStatus target, String condition) {
        String depotPayment = """
                exists (select 1 from payment p join sale s on s.id = %s
                        where p.invoice_id = %s and p.payment_date > s.sale_date and %s)""";
        int invoices = jdbc.update("update invoice i set payment_status = ? where i.payment_status = 'PAID' and "
                + depotPayment.formatted("i.sale_id", "i.id", condition), target.name());
        jdbc.update("update sale s0 set payment_status = ? where s0.payment_status = 'PAID' and exists ("
                + "select 1 from invoice i where i.sale_id = s0.id and i.payment_status = ?)",
                target.name(), target.name());
        return invoices;
    }

    /** The column's declared type, e.g. {@code ENUM('PAID', 'UNPAID')}; null when not found. */
    private String columnType(String table) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            // The stored case depends on the database: H2 folds to upper case, others do not.
            for (String name : List.of(table, table.toUpperCase(Locale.ROOT))) {
                for (String column : List.of(COLUMN, COLUMN.toUpperCase(Locale.ROOT))) {
                    try (ResultSet columns = metaData.getColumns(null, null, name, column)) {
                        if (columns.next()) {
                            return columns.getString("TYPE_NAME");
                        }
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture du schema impossible : migration des statuts abandonnee.", e);
        }
    }
}
