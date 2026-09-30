package com.houssen.liberoshop.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An installation from before the depot cash statuses: H2 enum columns that only know PAID and
 * UNPAID, and orders paid to a storekeeper recorded as PAID.
 */
class PaymentStatusMigrationTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void oldSchema() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table sale (id bigint primary key, sale_date timestamp, "
                + "payment_status enum('PAID','UNPAID'))");
        jdbc.execute("create table invoice (id bigint primary key, sale_id bigint, "
                + "payment_status enum('PAID','UNPAID'))");
        jdbc.execute("create table cash_remittance (id bigint primary key, status enum('CONFIRMED','PENDING'))");
        jdbc.execute("create table payment (id bigint primary key, invoice_id bigint, payment_date timestamp, "
                + "cash_remittance_id bigint)");
        jdbc.execute("insert into cash_remittance values (1, 'CONFIRMED'), (2, 'PENDING')");

        // 1: paid at the desk -- payment at the instant of the sale
        order(1, "PAID", "2026-09-30 09:00:00", "2026-09-30 09:00:00", null);
        // 2: paid at the depot, cash still in hand
        order(2, "PAID", "2026-09-30 09:00:00", "2026-09-30 11:00:00", null);
        // 3: paid at the depot, slip pending
        order(3, "PAID", "2026-09-30 09:00:00", "2026-09-30 11:00:00", 2L);
        // 4: paid at the depot, slip confirmed
        order(4, "PAID", "2026-09-30 09:00:00", "2026-09-30 11:00:00", 1L);
        // 5: not paid at all
        order(5, "UNPAID", "2026-09-30 09:00:00", null, null);
    }

    private void order(long id, String status, String saleDate, String paymentDate, Long remittance) {
        jdbc.update("insert into sale values (?, ?, ?)", id, saleDate, status);
        jdbc.update("insert into invoice values (?, ?, ?)", id, id, status);
        if (paymentDate != null) {
            jdbc.update("insert into payment values (?, ?, ?, ?)", id, id, paymentDate, remittance);
        }
    }

    private String invoiceStatus(long id) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, id);
    }

    private String saleStatus(long id) {
        return jdbc.queryForObject("select payment_status from sale where id = ?", String.class, id);
    }

    @Test
    @DisplayName("widens the columns and puts the depot's cash back where it really is")
    void migratesAnOldInstallation() {
        new PaymentStatusMigration(dataSource).run(new DefaultApplicationArguments());

        assertEquals("PAID", invoiceStatus(1));
        assertEquals("COLLECTED", invoiceStatus(2));
        assertEquals("REMITTED", invoiceStatus(3));
        assertEquals("PAID", invoiceStatus(4));
        assertEquals("UNPAID", invoiceStatus(5));
        assertEquals("COLLECTED", saleStatus(2), "the sale follows its invoice");
        assertEquals("REMITTED", saleStatus(3));
    }

    @Test
    @DisplayName("changes nothing the second time")
    void idempotent() {
        PaymentStatusMigration migration = new PaymentStatusMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        migration.run(new DefaultApplicationArguments());

        assertEquals("COLLECTED", invoiceStatus(2));
        assertEquals("REMITTED", invoiceStatus(3));
        assertEquals("PAID", invoiceStatus(4));
    }
}
