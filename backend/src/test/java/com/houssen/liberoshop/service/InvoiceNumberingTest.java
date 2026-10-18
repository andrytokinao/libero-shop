package com.houssen.liberoshop.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Invoice numbers follow one another: a sale that fails gives its number back. */
@SpringBootTest
class InvoiceNumberingTest {

    @Autowired
    private InvoiceNumbering numbering;
    @Autowired
    private TransactionTemplate transactions;

    private long numberOf(String invoiceNumber) {
        return Long.parseLong(invoiceNumber.substring(2));
    }

    @Test
    @DisplayName("une vente annulee par la base rend son numero : la suivante le reprend, sans trou")
    void numeroRenduSansTrou() {
        long before = numberOf(transactions.execute(status -> numbering.next()));

        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            numbering.next();
            throw new IllegalStateException("la vente echoue apres avoir pris son numero");
        }));
        long after = numberOf(transactions.execute(status -> numbering.next()));

        assertEquals(before + 1, after);
    }

    @Test
    @DisplayName("hors d'une vente, pas de numero : il ne se donne qu'avec la vente qui le porte")
    void seulementDansUneVente() {
        assertThrows(Exception.class, () -> numbering.next());
    }
}
