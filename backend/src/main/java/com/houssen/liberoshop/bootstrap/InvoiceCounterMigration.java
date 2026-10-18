package com.houssen.liberoshop.bootstrap;

import com.houssen.liberoshop.entity.InvoiceCounter;
import com.houssen.liberoshop.service.InvoiceNumbering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Sets the invoice counter after the highest number already given, so a shop whose invoices were
 * numbered from the sale identifiers carries on at the next one: no duplicate, no new gap.
 *
 * <p>Run on every start: an installation of any age gets its counter, and a counter found behind
 * an invoice number -- a database put together by hand -- is moved past it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class InvoiceCounterMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InvoiceCounterMigration.class);

    private final JdbcTemplate jdbc;

    public InvoiceCounterMigration(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        long next;
        Long current;
        try {
            Long highest = jdbc.queryForObject(
                    "select max(cast(substring(invoice_number, 3) as bigint)) from invoice"
                            + " where invoice_number like 'F-%'", Long.class);
            next = highest == null ? InvoiceNumbering.FIRST_NUMBER : Math.max(highest + 1, InvoiceNumbering.FIRST_NUMBER);
            current = jdbc.query("select next_number from invoice_counter where id = ?",
                    rs -> rs.next() ? rs.getLong(1) : null, InvoiceCounter.SINGLE);
        } catch (DataAccessException e) {
            // Tables not created yet: the counter starts itself at the first sale.
            log.debug("Compteur de factures non prepare : {}", e.getMessage());
            return;
        }
        if (current == null) {
            jdbc.update("insert into invoice_counter (id, next_number) values (?, ?)", InvoiceCounter.SINGLE, next);
            log.info("Compteur de factures prepare : prochaine facture F-{}.", next);
        } else if (current < next) {
            jdbc.update("update invoice_counter set next_number = ? where id = ?", next, InvoiceCounter.SINGLE);
            log.info("Compteur de factures avance a F-{} : il etait derriere la derniere facture.", next);
        }
    }
}
