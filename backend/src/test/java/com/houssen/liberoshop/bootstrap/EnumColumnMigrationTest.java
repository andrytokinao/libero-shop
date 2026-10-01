package com.houssen.liberoshop.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An installation from before "Prise de commande" and cancelled orders: H2 enum columns that only
 * know the constants of their day.
 */
class EnumColumnMigrationTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void oldSchema() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table user_app_role (user_id bigint, "
                + "role enum('CASHIER','DEPOT_AGENT','DEPOT_MANAGER','SUPER_ADMIN'))");
        jdbc.execute("create table invoice (id bigint, delivery_status enum('PENDING','DELIVERED'))");
        jdbc.update("insert into user_app_role values (1, 'CASHIER')");
        jdbc.update("insert into invoice values (1, 'DELIVERED')");
    }

    @Test
    @DisplayName("widens the columns so the new constants can be stored, keeping the existing rows")
    void widensTheColumns() {
        EnumColumnMigration migration = new EnumColumnMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        // A second start finds the columns complete and leaves them alone.
        migration.run(new DefaultApplicationArguments());

        assertDoesNotThrow(() -> jdbc.update("insert into user_app_role values (2, 'ORDER_TAKER')"));
        assertDoesNotThrow(() -> jdbc.update("insert into invoice values (2, 'CANCELLED')"));
        assertEquals("CASHIER", jdbc.queryForObject(
                "select role from user_app_role where user_id = 1", String.class));
        assertEquals("DELIVERED", jdbc.queryForObject(
                "select delivery_status from invoice where id = 1", String.class));
    }
}
