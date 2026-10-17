package com.houssen.liberoshop.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

/**
 * Indexes the date columns every period is read by -- today's sales, the invoices of a month,
 * the payments of the day, a product's history -- so a shop with years of tickets still opens
 * its screens at once.
 *
 * <p>Created when missing, on every start: an installation of any age gets them, and a second
 * start finds nothing to do. {@code ddl-auto=update} would not add them to an existing table.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class DateIndexMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DateIndexMigration.class);

    /** Table, column: the periods the screens filter and sort by. */
    static final List<String[]> DATE_COLUMNS = List.of(
            new String[] {"sale", "sale_date"},
            new String[] {"invoice", "invoice_date"},
            new String[] {"payment", "payment_date"},
            new String[] {"stock_movement", "movement_date"});

    private final JdbcTemplate jdbc;

    public DateIndexMigration(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String[] dateColumn : DATE_COLUMNS) {
            String table = dateColumn[0];
            String column = dateColumn[1];
            try {
                jdbc.execute("create index if not exists " + indexName(table, column)
                        + " on " + table + " (" + column + ")");
            } catch (DataAccessException e) {
                // A table not created yet, or a database without "if not exists": left as it is.
                log.debug("Index de {}.{} non cree : {}", table, column, e.getMessage());
            }
        }
    }

    static String indexName(String table, String column) {
        return "idx_" + table + "_" + column;
    }
}
