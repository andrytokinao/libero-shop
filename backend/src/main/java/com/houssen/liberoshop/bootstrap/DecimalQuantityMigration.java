package com.houssen.liberoshop.bootstrap;

import com.houssen.liberoshop.util.Quantities;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lets an installation from before loose goods store half a kilo.
 *
 * <p>Stock, movements and sale lines were whole numbers. An old database still holding them as
 * integers would truncate 1.75 kapoka to 1, or refuse it. Each column below that is still an
 * integer becomes a decimal, once, keeping its values -- 12 becomes 12.000. Any other type is
 * left alone.
 *
 * <p>On the current Hibernate, {@code ddl-auto=update} was seen widening these columns by itself
 * (an installation's copy came up already decimal, and this found nothing to do). That is not a
 * promise {@code ddl-auto=update} makes, so this stays as the guarantee rather than the hope.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class DecimalQuantityMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DecimalQuantityMigration.class);

    /** A column and the precision it is widened to. */
    record Target(String table, String column, int precision) {
    }

    static final List<Target> TARGETS = List.of(
            new Target("product", "stock_quantity", Quantities.PRECISION),
            new Target("stock_movement", "quantity", Quantities.PRECISION),
            new Target("sale_line", "quantity", 12));

    private static final Set<String> INTEGER_TYPES = Set.of("INTEGER", "INT", "BIGINT", "SMALLINT");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public DecimalQuantityMigration(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        TARGETS.forEach(this::widenIfNeeded);
    }

    private void widenIfNeeded(Target target) {
        String type = columnType(target);
        if (type == null || !INTEGER_TYPES.contains(type.toUpperCase(Locale.ROOT))) {
            return;
        }
        jdbc.execute("alter table " + target.table() + " alter column " + target.column()
                + " set data type numeric(" + target.precision() + ", " + Quantities.SCALE + ")");
        log.info("Colonne {}.{} passee en decimal ({} decimales).",
                target.table(), target.column(), Quantities.SCALE);
    }

    /** The column's declared type, e.g. {@code INTEGER}; null when not found. */
    private String columnType(Target target) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            // The stored case depends on the database: H2 folds to upper case, others do not.
            for (String name : List.of(target.table(), target.table().toUpperCase(Locale.ROOT))) {
                for (String column : List.of(target.column(), target.column().toUpperCase(Locale.ROOT))) {
                    try (ResultSet columns = metaData.getColumns(null, null, name, column)) {
                        if (columns.next()) {
                            return columns.getString("TYPE_NAME");
                        }
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture du schema impossible : migration des quantites abandonnee.", e);
        }
    }
}
