package com.houssen.liberoshop.service.delivery.dto;

import com.houssen.liberoshop.entity.DeliveryNote;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One line of the history of delivery notes: what came in, what it cost, what was paid on top. */
public record DeliveryNoteSummaryResponse(
        Long id,
        LocalDateTime date,
        Long supplierId,              // null: no supplier named
        String supplierName,
        Long performedById,
        BigDecimal purchaseTotal,     // the supplier's invoice
        BigDecimal chargesTotal,      // transport, loading...
        BigDecimal grandTotal,        // purchaseTotal + chargesTotal
        int lines,
        int losses,
        boolean withTransformation,
        boolean cancelled
) {

    public static DeliveryNoteSummaryResponse of(DeliveryNote note, long lines, long losses) {
        BigDecimal purchase = orZero(note.getPurchaseTotal());
        BigDecimal charges = orZero(note.getChargesTotal());
        return new DeliveryNoteSummaryResponse(
                note.getId(),
                note.getDate(),
                note.getSupplier() == null ? null : note.getSupplier().getId(),
                note.getSupplier() == null ? null : note.getSupplier().getName(),
                note.getPerformedBy().getId(),
                purchase,
                charges,
                purchase.add(charges),
                (int) lines,
                (int) losses,
                note.isWithTransformation(),
                note.isCancelled());
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
