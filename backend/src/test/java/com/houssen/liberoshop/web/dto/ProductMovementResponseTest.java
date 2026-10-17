package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.Invoice;
import com.houssen.liberoshop.entity.StockAdjustment;
import com.houssen.liberoshop.entity.StockOutput;
import com.houssen.liberoshop.entity.UserApp;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A line of a product's history, as the "Historique du stock" dialog reads it. */
class ProductMovementResponseTest {

    private final UserApp nadia = UserApp.builder().id(4L).fullName("Nadia Rasolofo").build();

    @Test
    void sortieDonneLaFactureAOuvrir() {
        Invoice invoice = Invoice.builder().id(31L).invoiceNumber("FAC-2026-00031").build();
        StockOutput output = StockOutput.builder().id(9L).invoice(invoice).quantity(new BigDecimal("3"))
                .movementDate(LocalDateTime.of(2026, 10, 16, 9, 0)).performedBy(nadia).build();

        ProductMovementResponse line = ProductMovementResponse.of(output);

        assertEquals(31L, line.invoiceId());
        assertEquals("FAC-2026-00031", line.invoiceNumber());
        assertEquals(new BigDecimal("-3"), line.quantity());
    }

    @Test
    void correctionSansFacture() {
        StockAdjustment adjustment = StockAdjustment.builder().id(10L).quantity(new BigDecimal("-1"))
                .movementDate(LocalDateTime.of(2026, 10, 16, 9, 0)).performedBy(nadia).build();

        ProductMovementResponse line = ProductMovementResponse.of(adjustment);

        assertNull(line.invoiceId());
        assertNull(line.invoiceNumber());
    }
}
