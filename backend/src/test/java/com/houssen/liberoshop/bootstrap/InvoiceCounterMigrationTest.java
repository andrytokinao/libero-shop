package com.houssen.liberoshop.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An installation whose invoices were numbered from the sale identifiers, then upgraded. */
class InvoiceCounterMigrationTest {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void schema() {
        dataSource = new SingleConnectionDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "", true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table invoice (id bigint, invoice_number varchar(255))");
        jdbc.execute("create table invoice_counter (id bigint primary key, next_number bigint not null)");
    }

    private long next() {
        return jdbc.queryForObject("select next_number from invoice_counter where id = 1", Long.class);
    }

    private void migrate() {
        new InvoiceCounterMigration(dataSource).run(new DefaultApplicationArguments());
    }

    @Test
    @DisplayName("une boutique qui a deja des factures reprend au numero suivant, sans doublon ni trou")
    void repriseApresLaDerniere() {
        jdbc.update("insert into invoice values (1, 'F-1001'), (2, 'F-1002'), (3, 'F-1065')");

        migrate();
        migrate();

        assertEquals(1066, next());
    }

    @Test
    @DisplayName("une boutique neuve commence a F-1001")
    void boutiqueNeuve() {
        migrate();

        assertEquals(1001, next());
    }

    @Test
    @DisplayName("un compteur en retard sur une facture est avance, jamais recule")
    void compteurEnRetardAvance() {
        jdbc.update("insert into invoice values (1, 'F-1200')");
        jdbc.update("insert into invoice_counter values (1, 1100)");

        migrate();
        assertEquals(1201, next());

        jdbc.update("update invoice_counter set next_number = 1500 where id = 1");
        migrate();
        assertEquals(1500, next());
    }
}
