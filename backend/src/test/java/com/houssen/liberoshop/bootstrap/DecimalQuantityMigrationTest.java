package com.houssen.liberoshop.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An installation from before loose goods: stock, movements and sale lines in whole numbers. */
class DecimalQuantityMigrationTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void oldSchema() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table product (id bigint, stock_quantity integer not null)");
        jdbc.execute("create table stock_movement (id bigint, quantity integer not null)");
        jdbc.execute("create table sale_line (id bigint, quantity integer not null)");
        jdbc.update("insert into product values (1, 12)");
        jdbc.update("insert into stock_movement values (1, 5)");
        jdbc.update("insert into sale_line values (1, 3)");
    }

    @Test
    @DisplayName("turns the quantity columns into decimals, keeping the existing values")
    void widensTheColumns() {
        DecimalQuantityMigration migration = new DecimalQuantityMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        // A second start finds the columns already decimal and leaves them alone.
        migration.run(new DefaultApplicationArguments());

        jdbc.update("insert into product values (2, 1.75)");
        jdbc.update("insert into stock_movement values (2, 0.875)");
        jdbc.update("insert into sale_line values (2, 0.5)");

        assertEquals(0, new BigDecimal("12").compareTo(
                jdbc.queryForObject("select stock_quantity from product where id = 1", BigDecimal.class)));
        assertEquals(0, new BigDecimal("1.75").compareTo(
                jdbc.queryForObject("select stock_quantity from product where id = 2", BigDecimal.class)));
        assertEquals(0, new BigDecimal("0.875").compareTo(
                jdbc.queryForObject("select quantity from stock_movement where id = 2", BigDecimal.class)));
        assertEquals(0, new BigDecimal("0.5").compareTo(
                jdbc.queryForObject("select quantity from sale_line where id = 2", BigDecimal.class)));
    }
}
