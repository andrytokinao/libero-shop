package com.houssen.liberoshop.bootstrap;

import com.houssen.liberoshop.entity.AdjustmentReason;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lets an installation created before cancellations existed record one.
 *
 * <p>When Hibernate created {@code stock_movement}, it listed the correction reasons of the day in
 * a check constraint on {@code reason} -- {@code reason in ('BREAKAGE', ... ,'OTHER')} -- and
 * {@code ddl-auto=update} never revisits a constraint: the first cancellation after the upgrade
 * ({@link AdjustmentReason#CANCELLATION}) would be refused by the database. As in
 * {@link MovementTypeMigration}, the outdated list is dropped rather than widened: the reason is
 * written from the enum alone.
 *
 * <p>Only a list of reasons that lacks one of today's is touched; the other checks of the table
 * stay.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class AdjustmentReasonMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdjustmentReasonMigration.class);

    private static final String TABLE = "stock_movement";
    private static final String COLUMN = "REASON";
    /** Every reason a correction may carry today. */
    static final List<String> REASONS = Arrays.stream(AdjustmentReason.values()).map(Enum::name).toList();

    private final JdbcTemplate jdbc;

    public AdjustmentReasonMigration(DataSource dataSource) {
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
            if (isOutdatedReasonList(clause)) {
                jdbc.execute("alter table " + TABLE + " drop constraint \"" + check.get("name") + "\"");
                log.info("Contrainte {} de {} levee : elle ne connaissait pas tous les motifs de correction.",
                        check.get("name"), TABLE);
            }
        }
    }

    /** A list of allowed reasons that misses at least one of today's. */
    private static boolean isOutdatedReasonList(String clause) {
        return clause.contains(COLUMN) && clause.contains(" IN")
                && !REASONS.stream().allMatch(reason -> clause.contains("'" + reason + "'"));
    }
}
