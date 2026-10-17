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
 * An installation from before cancellations: a {@code stock_movement} table whose check
 * constraint on {@code reason} only knows the five reasons of the day, as Hibernate wrote it then.
 */
class AdjustmentReasonMigrationTest {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void oldSchema() {
        // One connection kept open, as a pool keeps its own: H2 can no longer evaluate a check
        // constraint once the connection that created it has been closed.
        dataSource = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "", true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table stock_movement (id bigint, movement_type varchar(31) not null, "
                + "reason varchar(30) check ((reason in ('BREAKAGE','THEFT','EXPIRED','INVENTORY_COUNT','OTHER'))), "
                + "check (movement_type in ('SUPPLY','OUTPUT','ADJUSTMENT')))");
        jdbc.update("insert into stock_movement values (1, 'ADJUSTMENT', 'BREAKAGE')");
    }

    @Test
    @DisplayName("Avant la reprise, la base refuse une annulation")
    void theOldSchemaRefusesACancellation() {
        assertThrows(DataAccessException.class,
                () -> jdbc.update("insert into stock_movement values (2, 'ADJUSTMENT', 'CANCELLATION')"));
    }

    @Test
    @DisplayName("La liste des motifs est levee, les lignes et l'autre contrainte restent")
    void dropsTheOutdatedList() {
        AdjustmentReasonMigration migration = new AdjustmentReasonMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        // A second start finds nothing left to drop.
        migration.run(new DefaultApplicationArguments());

        assertDoesNotThrow(() -> jdbc.update("insert into stock_movement values (2, 'ADJUSTMENT', 'CANCELLATION')"));
        assertEquals("BREAKAGE", jdbc.queryForObject("select reason from stock_movement where id = 1", String.class));
        // The movement types are still checked.
        assertThrows(DataAccessException.class,
                () -> jdbc.update("insert into stock_movement values (3, 'AUTRE', null)"));
    }

    @Test
    @DisplayName("Une table deja a jour n'est pas touchee")
    void leavesACurrentSchemaAlone() {
        jdbc.execute("drop table stock_movement");
        jdbc.execute("create table stock_movement (id bigint, movement_type varchar(31) not null, reason varchar(30) "
                + "check ((reason in ('BREAKAGE','THEFT','EXPIRED','INVENTORY_COUNT','OTHER','CANCELLATION'))))");

        new AdjustmentReasonMigration(dataSource).run(new DefaultApplicationArguments());

        assertThrows(DataAccessException.class,
                () -> jdbc.update("insert into stock_movement values (1, 'ADJUSTMENT', 'AUTRE')"));
    }
}
