package com.houssen.liberoshop.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An installation from before stock corrections: a {@code stock_movement} table whose check
 * constraint only knows supplies and outputs, exactly as Hibernate wrote it then.
 */
class MovementTypeMigrationTest {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void oldSchema() {
        // One connection kept open, as a pool keeps its own: H2 can no longer evaluate a check
        // constraint once the connection that created it has been closed.
        dataSource = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "", true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table stock_movement (id bigint, invoice_id bigint, "
                + "movement_type varchar(31) not null check ((movement_type in ('OUTPUT','SUPPLY'))), "
                + "check (movement_type <> 'OUTPUT' or (invoice_id is not null)))");
        jdbc.update("insert into stock_movement values (1, null, 'SUPPLY')");
    }

    @Test
    @DisplayName("Avant la reprise, la base refuse une correction")
    void theOldSchemaRefusesAnAdjustment() {
        assertThrows(DataAccessException.class,
                () -> jdbc.update("insert into stock_movement values (2, null, 'ADJUSTMENT')"));
    }

    @Test
    @DisplayName("La liste des types est levee, les lignes et l'autre contrainte restent")
    void dropsTheOutdatedList() {
        MovementTypeMigration migration = new MovementTypeMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        // A second start finds nothing left to drop.
        migration.run(new DefaultApplicationArguments());

        assertDoesNotThrow(() -> jdbc.update("insert into stock_movement values (2, null, 'ADJUSTMENT')"));
        assertEquals("SUPPLY", jdbc.queryForObject(
                "select movement_type from stock_movement where id = 1", String.class));
        // An output still has to name its invoice.
        assertThrows(DataAccessException.class,
                () -> jdbc.update("insert into stock_movement values (3, null, 'OUTPUT')"));
    }

    @Test
    @DisplayName("Une table deja a jour n'est pas touchee")
    void leavesACurrentSchemaAlone() {
        jdbc.execute("drop table stock_movement");
        jdbc.execute("create table stock_movement (id bigint, invoice_id bigint, movement_type varchar(31) "
                + "not null check ((movement_type in ('ADJUSTMENT','OUTPUT','SUPPLY'))))");

        new MovementTypeMigration(dataSource).run(new DefaultApplicationArguments());

        assertThrows(DataAccessException.class,
                () -> jdbc.update("insert into stock_movement values (1, null, 'AUTRE')"));
    }
}
