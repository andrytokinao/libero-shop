package com.houssen.liberoshop.service;

import com.houssen.liberoshop.entity.InvoiceCounter;
import com.houssen.liberoshop.repository.InvoiceCounterRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * How an invoice number is given, in one place: "F-" and the next number of the counter.
 *
 * <p>Taken within the sale's transaction, under a lock: two tills checking out at the same
 * instant take turns, and a sale that fails -- refused by the database, sent twice -- gives its
 * number back with everything else. The numbers follow one another without gaps, as the books
 * expect. The counter is set up at start-up ({@code InvoiceCounterMigration}) after the highest
 * number already given, so an existing shop carries on where it was.
 */
@Component
public class InvoiceNumbering {

    /** A fresh installation's first invoice reads F-1001 rather than F-1. */
    public static final long FIRST_NUMBER = 1001L;

    private final InvoiceCounterRepository counters;

    public InvoiceNumbering(InvoiceCounterRepository counters) {
        this.counters = counters;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next() {
        InvoiceCounter counter = counters.findForUpdate(InvoiceCounter.SINGLE)
                .orElseGet(() -> counters.save(new InvoiceCounter(InvoiceCounter.SINGLE, FIRST_NUMBER)));
        return "F-" + counter.take();
    }
}
