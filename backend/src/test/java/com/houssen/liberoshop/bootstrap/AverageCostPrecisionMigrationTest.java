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

/** An installation from before purchase units: the average purchase cost kept to the cent. */
class AverageCostPrecisionMigrationTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void oldSchema() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table product (id bigint, average_cost numeric(12, 2))");
        jdbc.update("insert into product values (1, 785.71)");
    }

    @Test
    @DisplayName("widens the average cost to six places, keeping the existing values")
    void widensTheColumn() {
        AverageCostPrecisionMigration migration = new AverageCostPrecisionMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        // A second start finds the column already wide and leaves it alone.
        migration.run(new DefaultApplicationArguments());

        jdbc.update("insert into product values (2, 785.714286)");

        assertEquals(new BigDecimal("785.710000"),
                jdbc.queryForObject("select average_cost from product where id = 1", BigDecimal.class));
        assertEquals(new BigDecimal("785.714286"),
                jdbc.queryForObject("select average_cost from product where id = 2", BigDecimal.class));
    }
}
