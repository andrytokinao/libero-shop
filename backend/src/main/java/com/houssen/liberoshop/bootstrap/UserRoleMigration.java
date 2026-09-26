package com.houssen.liberoshop.bootstrap;

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

/**
 * Moves the accounts of an installation created before roles became a set.
 *
 * <p>{@code UserApp} used to carry a single {@code role} column; it now carries a
 * {@code user_app_role} table. Hibernate's {@code ddl-auto=update} creates that table but
 * never fills it and never drops a column, so without this runner the first start after the
 * upgrade would leave every account -- the super-admin included -- with no role at all, and
 * the old {@code not null} column would then refuse every new account.
 *
 * <p>Runs before {@link DataInitializer}, which counts the accounts to decide whether the
 * database is empty. Does nothing at all on an installation that never had the old column,
 * which is every fresh one.
 *
 * <p>The column is dropped only once every account has at least one role in the new table:
 * losing the only copy of a role because a row could not be read would cost a shop its
 * access to its own sales.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserRoleMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserRoleMigration.class);

    private static final String LEGACY_COLUMN = "role";
    private static final String USERS_TABLE = "user_app";

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public UserRoleMigration(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!legacyColumnExists()) {
            return;
        }

        log.info("Colonne user_app.role heritee detectee : reprise des roles vers user_app_role.");
        // Idempotent: a second run after a partial reprise inserts nothing twice.
        int copied = jdbc.update("""
                insert into user_app_role (user_id, role)
                select u.id, u.role from user_app u
                where u.role is not null
                  and not exists (select 1 from user_app_role r
                                  where r.user_id = u.id and r.role = u.role)
                """);

        Integer roleless = jdbc.queryForObject("""
                select count(*) from user_app u
                where not exists (select 1 from user_app_role r where r.user_id = u.id)
                """, Integer.class);
        if (roleless == null || roleless > 0) {
            log.error("Reprise incomplete : {} compte(s) sans role. La colonne user_app.role est "
                    + "conservee et doit etre reprise a la main avant toute creation de compte.", roleless);
            return;
        }

        jdbc.execute("alter table user_app drop column " + LEGACY_COLUMN);
        log.info("Reprise terminee : {} role(s) repris, colonne user_app.role supprimee.", copied);
    }

    /** Absent on every installation created by this version, present on the earlier ones. */
    private boolean legacyColumnExists() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            // The stored case depends on the database: H2 folds to upper case, others do not.
            for (String table : List.of(USERS_TABLE, USERS_TABLE.toUpperCase())) {
                try (ResultSet columns = metaData.getColumns(null, null, table, null)) {
                    while (columns.next()) {
                        if (LEGACY_COLUMN.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture du schema impossible : reprise des roles abandonnee.", e);
        }
    }
}
