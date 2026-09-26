package com.houssen.liberoshop.bootstrap;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upgrade of an installation that is already selling.
 *
 * <p>This is the one step of the change that cannot be replayed: a shop whose roles were
 * lost could not even sign its own administrator in. So the legacy schema is rebuilt here by
 * hand, exactly as the previous version left it, and the migration is run against it.
 */
class UserRoleMigrationTest {

    @Test
    @DisplayName("moves the single role of each account into the role table, then drops the column")
    void movesTheLegacyColumn() {
        JdbcTemplate jdbc = legacySchema();
        jdbc.update("""
                insert into user_app (id, full_name, username, password, enabled, role) values
                  (1, 'Fatima Randria', 'fatima', 'hash', true, 'CASHIER'),
                  (2, 'Mparany Solofo', 'mparany', 'hash', true, 'SUPER_ADMIN')
                """);

        migrate(jdbc);

        assertEquals(List.of("CASHIER"), rolesOf(jdbc, 1L));
        assertEquals(List.of("SUPER_ADMIN"), rolesOf(jdbc, 2L));
        // Left behind, the not-null column would refuse every account created afterwards.
        assertFalse(hasLegacyColumn(jdbc));
    }

    @Test
    @DisplayName("runs twice without duplicating a role")
    void isIdempotent() {
        JdbcTemplate jdbc = legacySchema();
        jdbc.update("""
                insert into user_app (id, full_name, username, password, enabled, role)
                values (1, 'Fatima Randria', 'fatima', 'hash', true, 'CASHIER')
                """);
        jdbc.update("insert into user_app_role (user_id, role) values (1, 'CASHIER')");

        migrate(jdbc);

        assertEquals(List.of("CASHIER"), rolesOf(jdbc, 1L));
    }

    @Test
    @DisplayName("keeps the column when an account would be left with no role at all")
    void keepsTheColumnWhenARoleCouldNotBeRead() {
        JdbcTemplate jdbc = legacySchema();
        jdbc.update("""
                insert into user_app (id, full_name, username, password, enabled, role)
                values (1, 'Compte casse', 'casse', 'hash', true, null)
                """);

        migrate(jdbc);

        // Nothing to copy, so nothing is destroyed either: the shop keeps its only copy of
        // the data and the log says what has to be fixed by hand.
        assertTrue(hasLegacyColumn(jdbc));
    }

    @Test
    @DisplayName("does nothing on an installation that never had the column")
    void ignoresAFreshInstallation() {
        JdbcTemplate jdbc = freshSchema();
        jdbc.update("""
                insert into user_app (id, full_name, username, password, enabled)
                values (1, 'Soa Ravelo', 'soa', 'hash', true)
                """);
        jdbc.update("""
                insert into user_app_role (user_id, role) values (1, 'CASHIER'), (1, 'DEPOT_AGENT')
                """);

        migrate(jdbc);

        assertEquals(List.of("CASHIER", "DEPOT_AGENT"), rolesOf(jdbc, 1L));
    }

    // --------------------------------------------------------------------- fixtures

    /** The schema of the previous version: one role per account, in a not-null column. */
    private static JdbcTemplate legacySchema() {
        JdbcTemplate jdbc = freshSchema();
        jdbc.execute("alter table user_app add column role varchar(64)");
        return jdbc;
    }

    /** What Hibernate creates for the current entity. */
    private static JdbcTemplate freshSchema() {
        JdbcDataSource dataSource = new JdbcDataSource();
        // A database per test, so the order they run in decides nothing.
        dataSource.setUrl("jdbc:h2:mem:migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                create table user_app (
                  id bigint primary key,
                  full_name varchar(255) not null,
                  username varchar(255) not null unique,
                  password varchar(255) not null,
                  enabled boolean not null
                )
                """);
        jdbc.execute("""
                create table user_app_role (
                  user_id bigint not null,
                  role varchar(64) not null,
                  primary key (user_id, role)
                )
                """);
        return jdbc;
    }

    private static void migrate(JdbcTemplate jdbc) {
        DataSource dataSource = jdbc.getDataSource();
        new UserRoleMigration(dataSource).run(null);
    }

    private static List<String> rolesOf(JdbcTemplate jdbc, long userId) {
        return jdbc.queryForList("select role from user_app_role where user_id = ? order by role",
                String.class, userId);
    }

    private static boolean hasLegacyColumn(JdbcTemplate jdbc) {
        Integer found = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where upper(table_name) = 'USER_APP' and upper(column_name) = 'ROLE'
                """, Integer.class);
        return found != null && found > 0;
    }
}
