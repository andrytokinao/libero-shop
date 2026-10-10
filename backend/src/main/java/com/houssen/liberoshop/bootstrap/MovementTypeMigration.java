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
import java.util.Locale;
import java.util.Map;

/**
 * Lets an installation created before stock corrections existed record one.
 *
 * <p>When Hibernate created {@code stock_movement} it listed the movement types of the day in a
 * check constraint -- {@code movement_type in ('OUTPUT','SUPPLY')} -- and {@code ddl-auto=update}
 * never revisits a constraint: the first breakage booked after the upgrade would be refused by
 * the database. The same family of problem as {@link EnumColumnMigration}, with a different
 * remedy: the outdated list is dropped rather than widened, since the discriminator is written
 * by Hibernate alone and never by a hand that could mistype it.
 *
 * <p>Only a list that lacks a type is touched. The other check on that table -- an output must
 * name its invoice -- has no list, and stays.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class MovementTypeMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MovementTypeMigration.class);

    private static final String TABLE = "stock_movement";
    private static final String COLUMN = "MOVEMENT_TYPE";
    /** Every discriminator value of the {@code StockMovement} hierarchy. */
    static final List<String> TYPES = List.of("SUPPLY", "OUTPUT", "ADJUSTMENT");

    private final JdbcTemplate jdbc;

    public MovementTypeMigration(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> checks;
        try {
            checks = jdbc.queryForList("""
                    select tc.constraint_name as name, cc.check_clause as clause
                    from information_schema.table_constraints tc
                    join information_schema.check_constraints cc
                      on cc.constraint_schema = tc.constraint_schema
                     and cc.constraint_name = tc.constraint_name
                    where upper(tc.table_name) = ? and tc.constraint_type = 'CHECK'
                    """, TABLE.toUpperCase(Locale.ROOT));
        } catch (DataAccessException e) {
            // A database that does not describe its constraints this way is left as it is.
            log.debug("Contraintes de {} non lues : {}", TABLE, e.getMessage());
            return;
        }
        for (Map<String, Object> check : checks) {
            String clause = String.valueOf(check.get("clause")).toUpperCase(Locale.ROOT);
            if (isOutdatedTypeList(clause)) {
                jdbc.execute("alter table " + TABLE + " drop constraint \"" + check.get("name") + "\"");
                log.info("Contrainte {} de {} levee : elle ne connaissait pas tous les types de mouvement.",
                        check.get("name"), TABLE);
            }
        }
    }

    /** A list of allowed movement types that misses at least one of today's. */
    private static boolean isOutdatedTypeList(String clause) {
        return clause.contains(COLUMN) && clause.contains(" IN")
                && !TYPES.stream().allMatch(type -> clause.contains("'" + type + "'"));
    }
}
