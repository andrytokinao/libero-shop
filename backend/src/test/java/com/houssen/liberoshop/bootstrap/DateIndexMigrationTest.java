package com.houssen.liberoshop.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** An installation from before the date indexes: the four tables, without them. */
class DateIndexMigrationTest {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void oldSchema() {
        dataSource = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "", true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table sale (id bigint, sale_date timestamp)");
        jdbc.execute("create table invoice (id bigint, invoice_date timestamp)");
        jdbc.execute("create table payment (id bigint, payment_date timestamp)");
        jdbc.execute("create table stock_movement (id bigint, movement_date timestamp)");
    }

    private int indexesNamed(String name) {
        return jdbc.queryForObject(
                "select count(*) from information_schema.indexes where upper(index_name) = ?",
                Integer.class, name.toUpperCase());
    }

    @Test
    @DisplayName("les quatre colonnes de date recoivent leur index, une seule fois")
    void indexesCreatedOnce() {
        DateIndexMigration migration = new DateIndexMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        assertDoesNotThrow(() -> migration.run(new DefaultApplicationArguments()), "a second start");

        for (String[] dateColumn : DateIndexMigration.DATE_COLUMNS) {
            assertEquals(1, indexesNamed(DateIndexMigration.indexName(dateColumn[0], dateColumn[1])),
                    dateColumn[0] + "." + dateColumn[1]);
        }
    }

    @Test
    @DisplayName("une table absente ne bloque pas le demarrage")
    void missingTableIsSkipped() {
        jdbc.execute("drop table payment");

        assertDoesNotThrow(() -> new DateIndexMigration(dataSource).run(new DefaultApplicationArguments()));
        assertEquals(1, indexesNamed("idx_sale_sale_date"));
    }
}
